package com.jipsamoye.backend.domain.boardComment.dto.response;

import com.jipsamoye.backend.domain.boardComment.entity.BoardComment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "자유게시판 댓글 응답")
public record BoardCommentResponse(

        @Schema(description = "댓글 ID")
        Long id,

        @Schema(description = "작성자 닉네임")
        String nickname,

        @Schema(description = "작성자 프로필 이미지 URL")
        String profileImageUrl,

        @Schema(description = "댓글 내용")
        String content,

        @Schema(description = "작성일시")
        LocalDateTime createdAt
) {
    public static BoardCommentResponse from(BoardComment comment) {
        boolean isUserDeleted = comment.getUser().isDeleted();
        return new BoardCommentResponse(
                comment.getId(),
                isUserDeleted ? "탈퇴한 사용자" : comment.getUser().getNickname(),
                isUserDeleted ? null : comment.getUser().getProfileImageUrl(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
