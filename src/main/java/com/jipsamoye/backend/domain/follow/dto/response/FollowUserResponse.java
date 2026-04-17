package com.jipsamoye.backend.domain.follow.dto.response;

import com.jipsamoye.backend.domain.user.entity.User;

public record FollowUserResponse(
        Long id,
        String nickname,
        String profileImageUrl
) {
    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
