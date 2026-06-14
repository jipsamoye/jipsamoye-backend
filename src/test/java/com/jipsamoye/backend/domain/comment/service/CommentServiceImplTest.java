package com.jipsamoye.backend.domain.comment.service;

import com.jipsamoye.backend.domain.comment.dto.request.CommentCreateRequest;
import com.jipsamoye.backend.domain.comment.dto.request.CommentUpdateRequest;
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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private User deletedUser(long id, String nickname) {
        User u = user(id, nickname);
        ReflectionTestUtils.setField(u, "deletedAt", LocalDateTime.now());
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

    // ── create 테스트 ────────────────────────────────

    @Test
    @DisplayName("댓글 작성 시 응답에 작성자의 총 좋아요 수가 포함된다")
    void create_includesAuthorTotalLikeCount() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);

        when(petPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 20L);
            return c;
        });
        when(commentRepository.countByParentAndDeletedAtIsNull(any())).thenReturn(0L);
        when(petPostRepository.sumLikeCountByUserId(1L)).thenReturn(42L);

        CommentCreateRequest req = new CommentCreateRequest(10L, null, null, "댓글 내용");
        CommentResponse response = commentService.create(req, 1L);

        assertThat(response.authorTotalLikeCount()).isEqualTo(42L);
        verify(petPostRepository).sumLikeCountByUserId(1L);
    }

    @Test
    @DisplayName("최상위 댓글 작성 시 게시글 작성자에게 PET_POST_COMMENT 알림 이벤트가 발행된다")
    void create_topLevel_publishesPostCommentNotification() {
        User postOwner = user(1L, "게시글작성자");
        User commenter = user(2L, "댓글작성자");
        PetPost post = petPost(10L, postOwner);

        when(petPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(commenter));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });
        when(commentRepository.countByParentAndDeletedAtIsNull(any())).thenReturn(0L);

        CommentCreateRequest req = new CommentCreateRequest(10L, null, null, "댓글 내용");
        commentService.create(req, 2L);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo(NotificationType.PET_POST_COMMENT);
        assertThat(event.getReceiver().getId()).isEqualTo(1L);
        assertThat(event.getSender().getId()).isEqualTo(2L);
        assertThat(event.getRelatedPostId()).isEqualTo(10L);
        assertThat(event.getTargetId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("자기 게시글에 자기가 최상위 댓글을 달면 알림 이벤트가 발행되지 않는다")
    void create_topLevelOnOwnPost_doesNotPublishEvent() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);

        when(petPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 30L);
            return c;
        });
        when(commentRepository.countByParentAndDeletedAtIsNull(any())).thenReturn(0L);

        CommentCreateRequest req = new CommentCreateRequest(10L, null, null, "셀프 댓글");
        commentService.create(req, 1L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("답글 작성 시 게시글 작성자에게는 중복 알림이 가지 않고 부모 작성자에게만 발행된다")
    void create_reply_doesNotNotifyPostOwner() {
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
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo(NotificationType.PET_POST_COMMENT_REPLY);
        assertThat(event.getReceiver().getId()).isEqualTo(2L);
    }

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

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getReceiver().getId()).isEqualTo(3L);
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

    // ── update 테스트 ────────────────────────────────

    @Test
    @DisplayName("댓글 수정 시 응답에 작성자의 총 좋아요 수가 포함된다")
    void update_includesAuthorTotalLikeCount() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);
        Comment comment = parentComment(20L, author, post);

        when(commentRepository.findById(20L)).thenReturn(Optional.of(comment));
        when(commentRepository.countByParentAndDeletedAtIsNull(comment)).thenReturn(0L);
        when(petPostRepository.sumLikeCountByUserId(1L)).thenReturn(15L);

        CommentUpdateRequest req = new CommentUpdateRequest("수정된 내용");
        CommentResponse response = commentService.update(20L, req, 1L);

        assertThat(response.authorTotalLikeCount()).isEqualTo(15L);
        verify(petPostRepository).sumLikeCountByUserId(1L);
    }

    // ── delete 테스트 ────────────────────────────────

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

    // ── getCommentsByPost 테스트 ────────────────────────────────

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
        when(petPostRepository.sumLikeCountGroupedByUserIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 7L}));

        PageResponse<CommentResponse> result = commentService.getCommentsByPost(10L, pageable);

        assertThat(result.getContent()).hasSize(2);
        CommentResponse first = result.getContent().get(0);
        assertThat(first.replyCount()).isEqualTo(2L);
        assertThat(first.replies()).hasSize(2);
        assertThat(first.authorTotalLikeCount()).isEqualTo(7L);
        CommentResponse second = result.getContent().get(1);
        assertThat(second.replyCount()).isEqualTo(0L);
        assertThat(second.replies()).isEmpty();
        assertThat(second.authorTotalLikeCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("getCommentsByPost - 부모·답글 작성자가 섞여도 batch 쿼리가 정확히 1회 호출된다")
    void getCommentsByPost_batchLikeQueryCalledOnce() {
        User author1 = user(1L, "작성자1");
        User author2 = user(2L, "작성자2");
        PetPost post = petPost(10L, author1);

        Comment parent = parentComment(20L, author1, post);
        Comment reply = Comment.builder().petPost(post).user(author2).content("답글").parent(parent).build();
        ReflectionTestUtils.setField(reply, "id", 30L);

        Pageable pageable = PageRequest.of(0, 20);

        when(commentRepository.findParentsByPetPostId(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(parent)));
        when(commentRepository.countRepliesGroupedByParentIds(List.of(20L)))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 1L}));
        when(commentRepository.findTop3RepliesByParentIds(List.of(20L)))
                .thenReturn(List.of(reply));
        when(petPostRepository.sumLikeCountGroupedByUserIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 3L}, new Object[]{2L, 5L}));

        PageResponse<CommentResponse> result = commentService.getCommentsByPost(10L, pageable);

        verify(petPostRepository, times(1)).sumLikeCountGroupedByUserIds(anyCollection());
        assertThat(result.getContent().get(0).authorTotalLikeCount()).isEqualTo(3L);
        assertThat(result.getContent().get(0).replies().get(0).authorTotalLikeCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getCommentsByPost - 부모가 0건이면 batch 쿼리가 호출되지 않는다")
    void getCommentsByPost_emptyParents_noBatchQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(commentRepository.findParentsByPetPostId(10L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        commentService.getCommentsByPost(10L, pageable);

        verify(petPostRepository, never()).sumLikeCountGroupedByUserIds(anyCollection());
    }

    @Test
    @DisplayName("getCommentsByPost - 탈퇴한 작성자의 댓글은 authorTotalLikeCount가 0이다")
    void getCommentsByPost_deletedAuthor_likeCountIsZero() {
        User deletedAuthor = deletedUser(1L, "탈퇴자");
        User activeAuthor = user(2L, "활성유저");
        PetPost post = petPost(10L, activeAuthor);

        Comment parent = parentComment(20L, deletedAuthor, post);
        Comment reply = Comment.builder().petPost(post).user(activeAuthor).content("답글").parent(parent).build();
        ReflectionTestUtils.setField(reply, "id", 30L);

        Pageable pageable = PageRequest.of(0, 20);

        when(commentRepository.findParentsByPetPostId(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(parent)));
        when(commentRepository.countRepliesGroupedByParentIds(List.of(20L)))
                .thenReturn(List.of());
        when(commentRepository.findTop3RepliesByParentIds(List.of(20L)))
                .thenReturn(List.of(reply));
        when(petPostRepository.sumLikeCountGroupedByUserIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 99L}));

        PageResponse<CommentResponse> result = commentService.getCommentsByPost(10L, pageable);

        CommentResponse parentResponse = result.getContent().get(0);
        assertThat(parentResponse.authorTotalLikeCount()).isEqualTo(0L);
        assertThat(parentResponse.replies().get(0).authorTotalLikeCount()).isEqualTo(99L);
    }

    // ── getReplies 테스트 ────────────────────────────────

    @Test
    @DisplayName("답글 더보기 조회 시 오래된 순 단일 페이지가 반환되고 authorTotalLikeCount가 포함된다")
    void getReplies_returnsOldestFirstPage() {
        User author = user(1L, "작성자");
        PetPost post = petPost(10L, author);
        Comment parent = parentComment(20L, author, post);

        Comment reply = Comment.builder().petPost(post).user(author).content("답글").parent(parent).build();
        ReflectionTestUtils.setField(reply, "id", 30L);

        Pageable pageable = PageRequest.of(0, 20);
        when(commentRepository.findRepliesByParentId(20L, pageable))
                .thenReturn(new PageImpl<>(List.of(reply)));
        when(petPostRepository.sumLikeCountGroupedByUserIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 8L}));

        PageResponse<CommentResponse> result = commentService.getReplies(20L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(30L);
        assertThat(result.getContent().get(0).authorTotalLikeCount()).isEqualTo(8L);
    }
}
