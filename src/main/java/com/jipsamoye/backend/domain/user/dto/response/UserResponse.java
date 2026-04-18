package com.jipsamoye.backend.domain.user.dto.response;

import com.jipsamoye.backend.domain.user.entity.SocialLink;
import com.jipsamoye.backend.domain.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        String nickname,
        String bio,
        String profileImageUrl,
        String coverImageUrl,
        List<SocialLink> socialLinks,
        long postCount,
        long followerCount,
        long followingCount,
        LocalDateTime createdAt
) {
    public static UserResponse of(User user, long postCount, long followerCount, long followingCount) {
        return new UserResponse(
                user.getNickname(),
                user.getBio(),
                user.getProfileImageUrl(),
                user.getCoverImageUrl(),
                user.getSocialLinks(),
                postCount,
                followerCount,
                followingCount,
                user.getCreatedAt()
        );
    }
}
