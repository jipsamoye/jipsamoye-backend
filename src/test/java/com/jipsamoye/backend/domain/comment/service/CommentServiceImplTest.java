package com.jipsamoye.backend.domain.comment.service;

import com.jipsamoye.backend.domain.comment.dto.request.CommentCreateRequest;
import com.jipsamoye.backend.domain.comment.dto.response.CommentResponse;
import com.jipsamoye.backend.domain.comment.entity.Comment;
import com.jipsamoye.backend.domain.comment.repository.CommentRepository;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.notification.event.NotificationEvent;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
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
class CommentServiceImplTest {

    @InjectMocks private CommentServiceImpl commentService;

    @Mock private CommentRepository commentRepository;
    @Mock private PetPostRepository petPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private User user(long id, String nickname) {
        User u = User.builder()
                .nickname(nickname)
                .email(nickname + "@test.com")
                .provider(Provider.KAKAO)
                .providerId("p-" + id)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private PetPost petPost(long id, User owner) {
        PetPost p = PetPost.builder()
                .user(owner)
                .title("게시글")
                .content("내용")
                .build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private Comment parentComment(long id, User owner, PetPost post) {
        Comment c = Comment.builder()
                .petPost(post)
                .user(owner)
                .content("부모 댓글")
                .build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    // ── Task 7: create 테스트 ────────────────────────────────

    @Test
    @DisplayName("답글 작성 시 부모 작성자에게 알림 이벤트가 발행된다")
    void create_reply_publishesNotificationEvent() {
        User postOwner = user(1L, "게시글작성자");
        User parentAuthor = user(2L, "부모댓글작성자");
        User replyAuthor = user(3L, "답글작성자");
        PetPost post = petPost(10L, postOwner);
        Comment parent = parentComment(20L, parentAuthor, post);

        when(petPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(3L)).thenReturn(Optional.of(replyAuthor));
        when(commentRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });

        CommentCreateRequest req = new CommentCreateRequest(10L, 20L, null, "답글 내용");
        commentService.create(req, 3L);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo(NotificationType.PET_POST_COMMENT_REPLY);
        assertThat(event.getReceiver().getId()).isEqualTo(2L);
        assertThat(event.getSender().getId()).isEqualTo(3L);
        assertThat(event.getRelatedPostId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("자기 자신의 댓글에 답글을 달면 알림 이벤트가 발행되지 않는다")
    void create_selfReply_doesNotPublishEvent() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);
        Comment parent = parentComment(20L, author, post);

        when(petPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });

        CommentCreateRequest req = new CommentCreateRequest(10L, 20L, null, "셀프 답글");
        commentService.create(req, 1L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("답글의 답글 시 mentionedUser가 본인이면 알림 이벤트가 발행되지 않는다")
    void create_selfMention_doesNotPublishEvent() {
        User postOwner = user(1L, "게시글작성자");
        User rootAuthor = user(2L, "루트댓글작성자");
        User replyAuthor = user(3L, "답글작성자");
        PetPost post = petPost(10L, postOwner);

        Comment root = parentComment(20L, rootAuthor, post);
        // replyAuthor 본인의 답글에 본인이 또 답글을 다는 케이스
        Comment reply = Comment.builder()
                .petPost(post).user(replyAuthor).content("답글").parent(root).build();
        ReflectionTestUtils.setField(reply, "id", 21L);

        when(petPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(3L)).thenReturn(Optional.of(replyAuthor));
        when(commentRepository.findById(21L)).thenReturn(Optional.of(reply));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });

        CommentCreateRequest req = new CommentCreateRequest(10L, 21L, null, "셀프 답답글");
        commentService.create(req, 3L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("답글의 답글 시도 시 root로 자동 매핑되고 mentionedUser가 원 답글 작성자로 보존된다")
    void create_replyToReply_remapsRootAndPreservesMention() {
        User postOwner = user(1L, "게시글작성자");
        User rootAuthor = user(2L, "루트댓글작성자");
        User replyAuthor = user(3L, "답글작성자");
        User replyToReplyAuthor = user(4L, "답답글작성자");
        PetPost post = petPost(10L, postOwner);

        Comment root = parentComment(20L, rootAuthor, post);
        Comment reply = Comment.builder()
                .petPost(post)
                .user(replyAuthor)
                .content("답글")
                .parent(root)
                .build();
        ReflectionTestUtils.setField(reply, "id", 21L);

        when(petPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(4L)).thenReturn(Optional.of(replyToReplyAuthor));
        when(commentRepository.findById(21L)).thenReturn(Optional.of(reply));

        ArgumentCaptor<Comment> savedCaptor = ArgumentCaptor.forClass(Comment.class);
        when(commentRepository.save(savedCaptor.capture())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });

        CommentCreateRequest req = new CommentCreateRequest(10L, 21L, null, "답답글");
        commentService.create(req, 4L);

        Comment saved = savedCaptor.getValue();
        assertThat(saved.getParent()).isEqualTo(root);
        assertThat(saved.getMentionedUser()).isEqualTo(replyAuthor);

        // 알림은 root 작성자(A)가 아닌 원 답글 작성자(B)에게
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getReceiver().getId()).isEqualTo(3L); // replyAuthor = B
    }

    @Test
    @DisplayName("마스킹된 부모에 답글 달기 시도 시 BusinessException이 발생하고 save가 호출되지 않는다")
    void create_replyToMaskedParent_throwsException() {
        User postOwner = user(1L, "게시글작성자");
        User replyAuthor = user(2L, "답글작성자");
        PetPost post = petPost(10L, postOwner);
        Comment maskedParent = parentComment(20L, postOwner, post);
        maskedParent.mask();

        when(petPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(replyAuthor));
        when(commentRepository.findById(20L)).thenReturn(Optional.of(maskedParent));

        CommentCreateRequest req = new CommentCreateRequest(10L, 20L, null, "답글 시도");

        assertThatThrownBy(() -> commentService.create(req, 2L))
                .isInstanceOf(BusinessException.class);
        verify(commentRepository, never()).save(any());
    }

    // ── Task 8: delete 테스트 ────────────────────────────────

    @Test
    @DisplayName("답글 없는 부모 댓글 삭제 시 soft delete되고 commentCount가 1 감소한다")
    void delete_parentWithoutReplies_softDeletesAndDecrements() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);
        Comment parent = parentComment(20L, author, post);

        when(commentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(parent));
        when(commentRepository.countByParentAndDeletedAtIsNull(parent)).thenReturn(0L);

        commentService.delete(20L, 1L);

        assertThat(parent.getDeletedAt()).isNotNull();
        assertThat(parent.isMasked()).isFalse();
        verify(petPostRepository, times(1)).decrementCommentCount(10L);
    }

    @Test
    @DisplayName("답글 있는 부모 댓글 삭제 시 마스킹되고 commentCount는 변동 없다")
    void delete_parentWithReplies_masksAndKeepsCount() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);
        Comment parent = parentComment(20L, author, post);

        when(commentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(parent));
        when(commentRepository.countByParentAndDeletedAtIsNull(parent)).thenReturn(2L);

        commentService.delete(20L, 1L);

        assertThat(parent.isMasked()).isTrue();
        assertThat(parent.getDeletedAt()).isNull();
        verify(petPostRepository, never()).decrementCommentCount(any());
    }

    @Test
    @DisplayName("마스킹된 부모의 마지막 답글 삭제 시 부모도 soft delete되고 commentCount가 2 감소한다")
    void delete_lastReplyOfMaskedParent_cascadesParentDelete() {
        User parentAuthor = user(1L, "부모작성자");
        User replyAuthor = user(2L, "답글작성자");
        PetPost post = petPost(10L, parentAuthor);
        Comment parent = parentComment(20L, parentAuthor, post);
        parent.mask();

        Comment reply = Comment.builder()
                .petPost(post)
                .user(replyAuthor)
                .content("답글")
                .parent(parent)
                .build();
        ReflectionTestUtils.setField(reply, "id", 21L);

        when(commentRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(reply));
        when(commentRepository.countByParentAndDeletedAtIsNull(parent)).thenReturn(0L);

        commentService.delete(21L, 2L);

        assertThat(reply.getDeletedAt()).isNotNull();
        assertThat(parent.getDeletedAt()).isNotNull();
        verify(petPostRepository, times(2)).decrementCommentCount(10L);
    }

    @Test
    @DisplayName("다른 유저의 댓글 삭제 시도 시 FORBIDDEN 예외가 발생한다")
    void delete_otherUsersComment_throwsForbidden() {
        User author = user(1L, "작성자");
        User other = user(2L, "타인");
        PetPost post = petPost(10L, author);
        Comment comment = parentComment(20L, author, post);

        when(commentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(20L, 2L))
                .isInstanceOf(BusinessException.class);
        verify(petPostRepository, never()).decrementCommentCount(any());
    }

    // ── Task 9: getCommentsByPost / getReplies 테스트 ────────

    @Test
    @DisplayName("게시글 댓글 조회 시 부모에 답글과 replyCount가 올바르게 첨부된다")
    void getCommentsByPost_attachesRepliesAndCount() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);

        Comment parent1 = parentComment(20L, author, post);
        Comment parent2 = parentComment(21L, author, post);

        Comment reply1 = Comment.builder().petPost(post).user(author).content("답글1").parent(parent1).build();
        Comment reply2 = Comment.builder().petPost(post).user(author).content("답글2").parent(parent1).build();
        ReflectionTestUtils.setField(reply1, "id", 30L);
        ReflectionTestUtils.setField(reply2, "id", 31L);

        Pageable pageable = PageRequest.of(0, 20);

        when(commentRepository.findParentsByPetPostId(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(parent1, parent2)));
        when(commentRepository.countRepliesGroupedByParentIds(List.of(20L, 21L)))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 2L}));
        when(commentRepository.findTop3RepliesByParentIds(List.of(20L, 21L)))
                .thenReturn(List.of(reply1, reply2));

        PageResponse<CommentResponse> result = commentService.getCommentsByPost(10L, pageable);

        assertThat(result.getContent()).hasSize(2);
        CommentResponse first = result.getContent().get(0);
        assertThat(first.replyCount()).isEqualTo(2L);
        assertThat(first.replies()).hasSize(2);
        CommentResponse second = result.getContent().get(1);
        assertThat(second.replyCount()).isEqualTo(0L);
        assertThat(second.replies()).isEmpty();
    }

    @Test
    @DisplayName("답글 더보기 조회 시 오래된 순 단일 페이지가 반환된다")
    void getReplies_returnsOldestFirstPage() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);
        Comment parent = parentComment(20L, author, post);

        Comment reply = Comment.builder().petPost(post).user(author).content("답글").parent(parent).build();
        ReflectionTestUtils.setField(reply, "id", 30L);

        Pageable pageable = PageRequest.of(0, 20);
        when(commentRepository.findRepliesByParentId(20L, pageable))
                .thenReturn(new PageImpl<>(List.of(reply)));

        PageResponse<CommentResponse> result = commentService.getReplies(20L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(30L);
    }
}
