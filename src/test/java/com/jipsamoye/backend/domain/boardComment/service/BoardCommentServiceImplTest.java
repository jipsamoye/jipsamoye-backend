package com.jipsamoye.backend.domain.boardComment.service;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import com.jipsamoye.backend.domain.board.repository.BoardRepository;
import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentCreateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.response.BoardCommentResponse;
import com.jipsamoye.backend.domain.boardComment.entity.BoardComment;
import com.jipsamoye.backend.domain.boardComment.repository.BoardCommentRepository;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.notification.event.NotificationEvent;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardCommentServiceImplTest {

    @InjectMocks private BoardCommentServiceImpl boardCommentService;

    @Mock private BoardCommentRepository boardCommentRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private User user(long id, String nickname) {
        User u = User.builder()
                .nickname(nickname).email(nickname + "@test.com")
                .provider(Provider.KAKAO).providerId("p-" + id).role(Role.USER).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Board board(long id, User owner) {
        Board b = Board.builder()
                .user(owner).category(BoardCategory.GENERAL).title("게시글").content("내용").build();
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }

    private BoardComment parentComment(long id, User owner, Board b) {
        BoardComment c = BoardComment.builder().board(b).user(owner).content("부모 댓글").build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    // ── Task 7: create 테스트 ──────────────────────────────

    @Test
    @DisplayName("답글 작성 시 부모 작성자에게 알림 이벤트가 발행되고 relatedPostId=boardId이다")
    void create_reply_publishesNotificationEvent() {
        User boardOwner = user(1L, "게시글작성자");
        User parentAuthor = user(2L, "부모댓글작성자");
        User replyAuthor = user(3L, "답글작성자");
        Board b = board(10L, boardOwner);
        BoardComment parent = parentComment(20L, parentAuthor, b);

        when(boardRepository.findById(10L)).thenReturn(Optional.of(b));
        when(userRepository.findById(3L)).thenReturn(Optional.of(replyAuthor));
        when(boardCommentRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(boardCommentRepository.save(any())).thenAnswer(inv -> {
            BoardComment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });

        boardCommentService.create(new BoardCommentCreateRequest(10L, 20L, null, "답글"), 3L);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo(NotificationType.BOARD_COMMENT_REPLY);
        assertThat(event.getReceiver().getId()).isEqualTo(2L);
        assertThat(event.getSender().getId()).isEqualTo(3L);
        assertThat(event.getRelatedPostId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("자기 자신의 댓글에 답글을 달면 알림 이벤트가 발행되지 않는다")
    void create_selfReply_doesNotPublishEvent() {
        User author = user(1L, "작성자");
        Board b = board(10L, author);
        BoardComment parent = parentComment(20L, author, b);

        when(boardRepository.findById(10L)).thenReturn(Optional.of(b));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(boardCommentRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(boardCommentRepository.save(any())).thenAnswer(inv -> {
            BoardComment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });

        boardCommentService.create(new BoardCommentCreateRequest(10L, 20L, null, "셀프 답글"), 1L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("답글의 답글 시도 시 root로 자동 매핑되고 mentionedUser는 원 답글 작성자, 알림은 B에게")
    void create_replyToReply_remapsRootAndPreservesMention() {
        User boardOwner = user(1L, "게시글작성자");
        User rootAuthor = user(2L, "루트댓글작성자");
        User replyAuthor = user(3L, "답글작성자");
        User replyToReplyAuthor = user(4L, "답답글작성자");
        Board b = board(10L, boardOwner);

        BoardComment root = parentComment(20L, rootAuthor, b);
        BoardComment reply = BoardComment.builder().board(b).user(replyAuthor).content("답글").parent(root).build();
        ReflectionTestUtils.setField(reply, "id", 21L);

        when(boardRepository.findById(10L)).thenReturn(Optional.of(b));
        when(userRepository.findById(4L)).thenReturn(Optional.of(replyToReplyAuthor));
        when(boardCommentRepository.findById(21L)).thenReturn(Optional.of(reply));

        ArgumentCaptor<BoardComment> savedCaptor = ArgumentCaptor.forClass(BoardComment.class);
        when(boardCommentRepository.save(savedCaptor.capture())).thenAnswer(inv -> {
            BoardComment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });

        boardCommentService.create(new BoardCommentCreateRequest(10L, 21L, null, "답답글"), 4L);

        BoardComment saved = savedCaptor.getValue();
        assertThat(saved.getParent()).isEqualTo(root);
        assertThat(saved.getMentionedUser()).isEqualTo(replyAuthor);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getReceiver().getId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("마스킹된 부모에 답글 달기 시도 시 BusinessException이 발생하고 save가 호출되지 않는다")
    void create_replyToMaskedParent_throwsException() {
        User boardOwner = user(1L, "게시글작성자");
        User replyAuthor = user(2L, "답글작성자");
        Board b = board(10L, boardOwner);
        BoardComment maskedParent = parentComment(20L, boardOwner, b);
        maskedParent.mask();

        when(boardRepository.findById(10L)).thenReturn(Optional.of(b));
        when(userRepository.findById(2L)).thenReturn(Optional.of(replyAuthor));
        when(boardCommentRepository.findById(20L)).thenReturn(Optional.of(maskedParent));

        assertThatThrownBy(() -> boardCommentService.create(
                new BoardCommentCreateRequest(10L, 20L, null, "답글 시도"), 2L))
                .isInstanceOf(BusinessException.class);
        verify(boardCommentRepository, never()).save(any());
    }

    // ── Task 8: delete 테스트 ──────────────────────────────

    @Test
    @DisplayName("답글 없는 부모 댓글 삭제 시 soft delete되고 commentCount가 1 감소한다")
    void delete_parentWithoutReplies_softDeletesAndDecrements() {
        User author = user(1L, "작성자");
        Board b = board(10L, author);
        BoardComment parent = parentComment(20L, author, b);

        when(boardCommentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(parent));
        when(boardCommentRepository.countByParentAndDeletedAtIsNull(parent)).thenReturn(0L);

        boardCommentService.delete(20L, 1L);

        assertThat(parent.getDeletedAt()).isNotNull();
        assertThat(parent.isMasked()).isFalse();
        verify(boardRepository, times(1)).decrementCommentCount(10L);
    }

    @Test
    @DisplayName("답글 있는 부모 댓글 삭제 시 마스킹되고 commentCount는 변동 없다")
    void delete_parentWithReplies_masksAndKeepsCount() {
        User author = user(1L, "작성자");
        Board b = board(10L, author);
        BoardComment parent = parentComment(20L, author, b);

        when(boardCommentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(parent));
        when(boardCommentRepository.countByParentAndDeletedAtIsNull(parent)).thenReturn(2L);

        boardCommentService.delete(20L, 1L);

        assertThat(parent.isMasked()).isTrue();
        assertThat(parent.getDeletedAt()).isNull();
        verify(boardRepository, never()).decrementCommentCount(any());
    }

    @Test
    @DisplayName("마스킹된 부모의 마지막 답글 삭제 시 부모도 soft delete되고 commentCount가 2 감소한다")
    void delete_lastReplyOfMaskedParent_cascadesParentDelete() {
        User parentAuthor = user(1L, "부모작성자");
        User replyAuthor = user(2L, "답글작성자");
        Board b = board(10L, parentAuthor);
        BoardComment parent = parentComment(20L, parentAuthor, b);
        parent.mask();

        BoardComment reply = BoardComment.builder().board(b).user(replyAuthor).content("답글").parent(parent).build();
        ReflectionTestUtils.setField(reply, "id", 21L);

        when(boardCommentRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(reply));
        when(boardCommentRepository.countByParentAndDeletedAtIsNull(parent)).thenReturn(0L);

        boardCommentService.delete(21L, 2L);

        assertThat(reply.getDeletedAt()).isNotNull();
        assertThat(parent.getDeletedAt()).isNotNull();
        verify(boardRepository, times(2)).decrementCommentCount(10L);
    }

    @Test
    @DisplayName("다른 유저의 댓글 삭제 시도 시 FORBIDDEN 예외가 발생한다")
    void delete_otherUsersComment_throwsForbidden() {
        User author = user(1L, "작성자");
        Board b = board(10L, author);
        BoardComment comment = parentComment(20L, author, b);

        when(boardCommentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> boardCommentService.delete(20L, 2L))
                .isInstanceOf(BusinessException.class);
        verify(boardRepository, never()).decrementCommentCount(any());
    }

    // ── Task 9: getCommentsByBoard / getReplies 테스트 ─────

    @Test
    @DisplayName("게시글 댓글 조회 시 부모에 답글과 replyCount가 올바르게 첨부된다")
    void getCommentsByBoard_attachesRepliesAndCount() {
        User author = user(1L, "작성자");
        Board b = board(10L, author);

        BoardComment parent1 = parentComment(20L, author, b);
        BoardComment parent2 = parentComment(21L, author, b);
        BoardComment reply1 = BoardComment.builder().board(b).user(author).content("답글1").parent(parent1).build();
        BoardComment reply2 = BoardComment.builder().board(b).user(author).content("답글2").parent(parent1).build();
        ReflectionTestUtils.setField(reply1, "id", 30L);
        ReflectionTestUtils.setField(reply2, "id", 31L);

        Pageable pageable = PageRequest.of(0, 20);
        when(boardCommentRepository.findParentsByBoardId(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(parent1, parent2)));
        when(boardCommentRepository.countRepliesGroupedByParentIds(List.of(20L, 21L)))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 2L}));
        when(boardCommentRepository.findTop3RepliesByParentIds(List.of(20L, 21L)))
                .thenReturn(List.of(reply1, reply2));

        PageResponse<BoardCommentResponse> result = boardCommentService.getCommentsByBoard(10L, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).replyCount()).isEqualTo(2L);
        assertThat(result.getContent().get(0).replies()).hasSize(2);
        assertThat(result.getContent().get(1).replyCount()).isEqualTo(0L);
        assertThat(result.getContent().get(1).replies()).isEmpty();
    }

    @Test
    @DisplayName("답글 더보기 조회 시 오래된 순 단일 페이지가 반환된다")
    void getReplies_returnsOldestFirstPage() {
        User author = user(1L, "작성자");
        Board b = board(10L, author);
        BoardComment parent = parentComment(20L, author, b);
        BoardComment reply = BoardComment.builder().board(b).user(author).content("답글").parent(parent).build();
        ReflectionTestUtils.setField(reply, "id", 30L);

        Pageable pageable = PageRequest.of(0, 20);
        when(boardCommentRepository.findRepliesByParentId(20L, pageable))
                .thenReturn(new PageImpl<>(List.of(reply)));

        PageResponse<BoardCommentResponse> result = boardCommentService.getReplies(20L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(30L);
    }
}
