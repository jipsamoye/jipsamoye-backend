package com.jipsamoye.backend.domain.boardComment.dto.response;

import com.jipsamoye.backend.domain.boardComment.entity.BoardComment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "자유게시판 댓글 응답")
public record BoardCommentResponse(

        Long id,
        String content,
        String nickname,
        String profileImageUrl,
        String mentionedNickname,
        boolean isMasked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long replyCount,
        List<BoardCommentResponse> replies
) {
    public static BoardCommentResponse from(BoardComment comment, long replyCount, List<BoardCommentResponse> replies) {
        boolean isUserDeleted = comment.getUser().isDeleted();
        String mentionedNickname = comment.getMentionedUser() != null
                ? (comment.getMentionedUser().isDeleted() ? "탈퇴한 사용자" : comment.getMentionedUser().getNickname())
                : null;
        return new BoardCommentResponse(
                comment.getId(),
                comment.isMasked() ? null : comment.getContent(),
                isUserDeleted ? "탈퇴한 사용자" : comment.getUser().getNickname(),
                isUserDeleted ? null : comment.getUser().getProfileImageUrl(),
                mentionedNickname,
                comment.isMasked(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replyCount,
                replies
        );
    }

    public static BoardCommentResponse ofReply(BoardComment comment) {
        boolean isUserDeleted = comment.getUser().isDeleted();
        String mentionedNickname = comment.getMentionedUser() != null
                ? (comment.getMentionedUser().isDeleted() ? "탈퇴한 사용자" : comment.getMentionedUser().getNickname())
                : null;
        return new BoardCommentResponse(
                comment.getId(),
                comment.getContent(),
                isUserDeleted ? "탈퇴한 사용자" : comment.getUser().getNickname(),
                isUserDeleted ? null : comment.getUser().getProfileImageUrl(),
                mentionedNickname,
                false,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                0L,
                List.of()
        );
    }
}
