package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
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

    public static PetPostResponse from(PetPost petPost) {
        return PetPostResponse.builder()
                .id(petPost.getId())
                .title(petPost.getTitle())
                .content(petPost.getContent())
                .imageUrls(petPost.getImageUrls())
                .likeCount(petPost.getLikeCount())
                .userId(petPost.getUser().getId())
                .nickname(petPost.getUser().getNickname())
                .profileImageUrl(petPost.getUser().getProfileImageUrl())
                .createdAt(petPost.getCreatedAt())
                .updatedAt(petPost.getUpdatedAt())
                .build();
    }
}
