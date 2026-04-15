package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PetPostResponse {

    private Long id;
    private String title;
    private String content;
    private List<String> imageUrls;
    private int likeCount;
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 탈퇴한 유저의 게시글은 "탈퇴한 사용자"로 표시
    public static PetPostResponse from(PetPost petPost, ImageCdnConverter converter) {
        boolean isUserDeleted = petPost.getUser().isDeleted();
        return PetPostResponse.builder()
                .id(petPost.getId())
                .title(petPost.getTitle())
                .content(petPost.getContent())
                .imageUrls(converter.toCdnUrls(petPost.getImageUrls()))
                .likeCount(petPost.getLikeCount())
                .userId(petPost.getUser().getId())
                .nickname(isUserDeleted ? "탈퇴한 사용자" : petPost.getUser().getNickname())
                .profileImageUrl(isUserDeleted ? null : converter.toCdnUrl(petPost.getUser().getProfileImageUrl()))
                .createdAt(petPost.getCreatedAt())
                .updatedAt(petPost.getUpdatedAt())
                .build();
    }
}
