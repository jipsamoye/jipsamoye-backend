package com.jipsamoye.backend.domain.boardComment.entity;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoardCommentTest {

    private static User user() {
        return User.builder()
                .nickname("테스터")
                .email("tester@example.com")
                .provider(Provider.KAKAO)
                .providerId("provider-1")
                .role(Role.USER)
                .build();
    }

    private static Board board(User user) {
        return Board.builder()
                .user(user)
                .category(BoardCategory.GENERAL)
                .title("자유 게시글")
                .content("내용")
                .build();
    }

    @Test
    @DisplayName("parent가 null이면 부모 댓글, non-null이면 답글로 구분된다")
    void isReply_distinguishesParentAndChild() {
        User u = user();
        Board b = board(u);

        BoardComment parent = BoardComment.builder()
                .board(b).user(u).content("부모 댓글").build();

        BoardComment reply = BoardComment.builder()
                .board(b).user(u).content("답글").parent(parent).build();

        assertThat(parent.isReply()).isFalse();
        assertThat(reply.isReply()).isTrue();
    }

    @Test
    @DisplayName("mask() 호출 시 isMasked만 true가 되고 content는 원본 그대로 보존된다")
    void mask_setsFlagWithoutAlteringContent() {
        String originalContent = "원본 댓글 내용";
        BoardComment comment = BoardComment.builder()
                .board(board(user())).user(user()).content(originalContent).build();

        comment.mask();

        assertThat(comment.isMasked()).isTrue();
        assertThat(comment.getContent()).isEqualTo(originalContent);
    }

    @Test
    @DisplayName("isMasked 기본값은 false이다")
    void isMasked_defaultsFalse() {
        BoardComment comment = BoardComment.builder()
                .board(board(user())).user(user()).content("댓글").build();

        assertThat(comment.isMasked()).isFalse();
    }
}
