package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;

import java.time.LocalDateTime;

public record PetPostListResponse(
        Long id,
        String title,
        String thumbnailUrl,
        int likeCount,
        Long userId,
        String nickname,
        LocalDateTime createdAt
) {
    // 탈퇴한 유저의 게시글은 "탈퇴한 사용자"로 표시
    public static PetPostListResponse from(PetPost petPost) {
        boolean isUserDeleted = petPost.getUser().isDeleted();
        return new PetPostListResponse(
                petPost.getId(),
                petPost.getTitle(),
                petPost.getImageUrls().isEmpty() ? null : petPost.getImageUrls().get(0),
                petPost.getLikeCount(),
                petPost.getUser().getId(),
                isUserDeleted ? "탈퇴한 사용자" : petPost.getUser().getNickname(),
                petPost.getCreatedAt()
        );
    }
}
