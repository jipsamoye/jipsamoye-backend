package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PetPostListResponse {

    private Long id;
    private String title;
    private String thumbnailUrl;
    private int likeCount;
    private Long userId;
    private String nickname;
    private LocalDateTime createdAt;

    // 탈퇴한 유저의 게시글은 "탈퇴한 사용자"로 표시
    public static PetPostListResponse from(PetPost petPost) {
        boolean isUserDeleted = petPost.getUser().isDeleted();
        return PetPostListResponse.builder()
                .id(petPost.getId())
                .title(petPost.getTitle())
                .thumbnailUrl(petPost.getImageUrls().isEmpty() ? null : petPost.getImageUrls().get(0))
                .likeCount(petPost.getLikeCount())
                .userId(petPost.getUser().getId())
                .nickname(isUserDeleted ? "탈퇴한 사용자" : petPost.getUser().getNickname())
                .createdAt(petPost.getCreatedAt())
                .build();
    }
}
