package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;

import java.time.LocalDateTime;

public record PetPostListResponse(
        Long id,
        String title,
        String thumbnailUrl,
        int likeCount,
        int commentCount,
        String nickname,
        String profileImageUrl,
        LocalDateTime createdAt,
        boolean aiGenerated
) {
    // 탈퇴한 유저의 게시글은 닉네임을 "탈퇴한 사용자"로 마스킹하고 프로필 이미지는 null로 내려보낸다
    public static PetPostListResponse from(PetPost petPost) {
        boolean isUserDeleted = petPost.getUser().isDeleted();
        return new PetPostListResponse(
                petPost.getId(),
                petPost.getTitle(),
                petPost.getImageUrls().isEmpty() ? null : petPost.getImageUrls().get(0),
                petPost.getLikeCount(),
                petPost.getCommentCount(),
                isUserDeleted ? "탈퇴한 사용자" : petPost.getUser().getNickname(),
                isUserDeleted ? null : petPost.getUser().getProfileImageUrl(),
                petPost.getCreatedAt(),
                petPost.isAiGenerated()
        );
    }
}
