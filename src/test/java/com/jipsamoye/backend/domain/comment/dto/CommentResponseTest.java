package com.jipsamoye.backend.domain.comment.dto;

import com.jipsamoye.backend.domain.comment.dto.response.CommentResponse;
import com.jipsamoye.backend.domain.comment.entity.Comment;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommentResponseTest {

    private User activeUser(String nickname) {
        return User.builder()
                .nickname(nickname)
                .email(nickname + "@test.com")
                .provider(Provider.KAKAO)
                .providerId("p-" + nickname)
                .role(Role.USER)
                .build();
    }

    private User deletedUser(String nickname) {
        User u = activeUser(nickname);
        u.softDelete();
        return u;
    }

    private PetPost petPost(User user) {
        return PetPost.builder()
                .user(user)
                .title("게시글")
                .content("내용")
                .build();
    }

    private Comment comment(User user, PetPost post) {
        Comment c = Comment.builder()
                .petPost(post)
                .user(user)
                .content("댓글 내용")
                .build();
        ReflectionTestUtils.setField(c, "id", 1L);
        return c;
    }

    @Test
    @DisplayName("from() - 활성 유저의 authorTotalLikeCount가 그대로 반환된다")
    void from_activeUser_returnsGivenLikeCount() {
        User user = activeUser("작성자");
        PetPost post = petPost(user);
        Comment c = comment(user, post);

        CommentResponse response = CommentResponse.from(c, 0L, List.of(), 99L);

        assertThat(response.authorTotalLikeCount()).isEqualTo(99L);
    }

    @Test
    @DisplayName("from() - 탈퇴 유저는 authorTotalLikeCount가 입력값과 무관하게 0이다")
    void from_deletedUser_likeCountIsZero() {
        User user = deletedUser("탈퇴자");
        PetPost post = petPost(user);
        Comment c = comment(user, post);

        CommentResponse response = CommentResponse.from(c, 0L, List.of(), 999L);

        assertThat(response.authorTotalLikeCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ofReply() - 활성 유저의 authorTotalLikeCount가 그대로 반환된다")
    void ofReply_activeUser_returnsGivenLikeCount() {
        User user = activeUser("작성자");
        PetPost post = petPost(user);
        Comment parent = comment(user, post);
        Comment reply = Comment.builder()
                .petPost(post)
                .user(user)
                .content("답글")
                .parent(parent)
                .build();
        ReflectionTestUtils.setField(reply, "id", 2L);

        CommentResponse response = CommentResponse.ofReply(reply, 55L);

        assertThat(response.authorTotalLikeCount()).isEqualTo(55L);
        assertThat(response.replyCount()).isEqualTo(0L);
        assertThat(response.replies()).isEmpty();
    }

    @Test
    @DisplayName("ofReply() - 탈퇴 유저는 authorTotalLikeCount가 0이다")
    void ofReply_deletedUser_likeCountIsZero() {
        User user = deletedUser("탈퇴자");
        PetPost post = petPost(user);
        Comment parent = comment(user, post);
        Comment reply = Comment.builder()
                .petPost(post)
                .user(user)
                .content("답글")
                .parent(parent)
                .build();
        ReflectionTestUtils.setField(reply, "id", 2L);

        CommentResponse response = CommentResponse.ofReply(reply, 777L);

        assertThat(response.authorTotalLikeCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("from() - 탈퇴 유저는 nickname이 '탈퇴한 사용자', profileImageUrl이 null이다")
    void from_deletedUser_masksNicknameAndProfileImage() {
        User user = deletedUser("탈퇴자");
        PetPost post = petPost(user);
        Comment c = comment(user, post);

        CommentResponse response = CommentResponse.from(c, 0L, List.of(), 0L);

        assertThat(response.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.profileImageUrl()).isNull();
    }
}
