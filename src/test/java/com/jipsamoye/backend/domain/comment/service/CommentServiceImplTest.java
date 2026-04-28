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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

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
    }

    @Test
    @DisplayName("자기 자신에게 답글을 달면 알림 이벤트가 발행되지 않는다")
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
}
