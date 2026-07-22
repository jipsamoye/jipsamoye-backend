package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;

import java.time.LocalDateTime;
import java.util.List;

public record PetPostResponse(
        Long id,
        String title,
        String content,
        List<String> imageUrls,
        int likeCount,
        int commentCount,
        String nickname,
        String profileImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean aiGenerated
) {
    // 탈퇴한 유저의 게시글은 "탈퇴한 사용자"로 표시
    public static PetPostResponse from(PetPost petPost) {
        boolean isUserDeleted = petPost.getUser().isDeleted();
        return new PetPostResponse(
                petPost.getId(),
                petPost.getTitle(),
                petPost.getContent(),
                petPost.getImageUrls(),
                petPost.getLikeCount(),
                petPost.getCommentCount(),
                isUserDeleted ? "탈퇴한 사용자" : petPost.getUser().getNickname(),
                isUserDeleted ? null : petPost.getUser().getProfileImageUrl(),
                petPost.getCreatedAt(),
                petPost.getUpdatedAt(),
                petPost.isAiGenerated()
        );
    }
}
