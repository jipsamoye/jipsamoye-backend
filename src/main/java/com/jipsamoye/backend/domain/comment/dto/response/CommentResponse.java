package com.jipsamoye.backend.domain.comment.dto.response;

import com.jipsamoye.backend.domain.comment.entity.Comment;

import java.time.LocalDateTime;
import java.util.List;

public record CommentResponse(
        Long id,
        String content,
        String nickname,
        String profileImageUrl,
        String mentionedNickname,
        boolean isMasked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long replyCount,
        List<CommentResponse> replies,
        long authorTotalLikeCount
) {
    public static CommentResponse from(Comment comment, long replyCount, List<CommentResponse> replies, long authorTotalLikeCount) {
        boolean isUserDeleted = comment.getUser().isDeleted();
        String mentionedNickname = comment.getMentionedUser() != null
                ? (comment.getMentionedUser().isDeleted() ? "탈퇴한 사용자" : comment.getMentionedUser().getNickname())
                : null;
        return new CommentResponse(
                comment.getId(),
                comment.isMasked() ? null : comment.getContent(),
                isUserDeleted ? "탈퇴한 사용자" : comment.getUser().getNickname(),
                isUserDeleted ? null : comment.getUser().getProfileImageUrl(),
                mentionedNickname,
                comment.isMasked(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replyCount,
                replies,
                isUserDeleted ? 0L : authorTotalLikeCount
        );
    }

    public static CommentResponse ofReply(Comment comment, long authorTotalLikeCount) {
        boolean isUserDeleted = comment.getUser().isDeleted();
        String mentionedNickname = comment.getMentionedUser() != null
                ? (comment.getMentionedUser().isDeleted() ? "탈퇴한 사용자" : comment.getMentionedUser().getNickname())
                : null;
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                isUserDeleted ? "탈퇴한 사용자" : comment.getUser().getNickname(),
                isUserDeleted ? null : comment.getUser().getProfileImageUrl(),
                mentionedNickname,
                false,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                0L,
                List.of(),
                isUserDeleted ? 0L : authorTotalLikeCount
        );
    }
}
