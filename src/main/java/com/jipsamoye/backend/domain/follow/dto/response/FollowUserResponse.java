package com.jipsamoye.backend.domain.follow.dto.response;

import com.jipsamoye.backend.domain.user.entity.User;

public record FollowUserResponse(
        String nickname,
        String profileImageUrl
) {
    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
