package com.jipsamoye.backend.domain.user.dto.response;

import com.jipsamoye.backend.domain.user.entity.SocialLink;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class UserResponse {

    private Long id;
    private String nickname;
    private String bio;
    private String profileImageUrl;
    private String coverImageUrl;
    private List<SocialLink> socialLinks;
    private long postCount;
    private long followerCount;
    private long followingCount;
    private LocalDateTime createdAt;

    public static UserResponse of(User user, long postCount, long followerCount, long followingCount, ImageCdnConverter converter) {
        return UserResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .bio(user.getBio())
                .profileImageUrl(converter.toCdnUrl(user.getProfileImageUrl()))
                .coverImageUrl(converter.toCdnUrl(user.getCoverImageUrl()))
                .socialLinks(user.getSocialLinks())
                .postCount(postCount)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
