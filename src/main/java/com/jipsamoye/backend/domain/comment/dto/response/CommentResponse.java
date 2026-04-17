package com.jipsamoye.backend.domain.comment.dto.response;

import com.jipsamoye.backend.domain.comment.entity.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String content,
        Long userId,
        String nickname,
        String profileImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    // 탈퇴한 유저의 댓글은 "탈퇴한 사용자"로 표시
    public static CommentResponse from(Comment comment) {
        boolean isUserDeleted = comment.getUser().isDeleted();
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUser().getId(),
                isUserDeleted ? "탈퇴한 사용자" : comment.getUser().getNickname(),
                isUserDeleted ? null : comment.getUser().getProfileImageUrl(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
