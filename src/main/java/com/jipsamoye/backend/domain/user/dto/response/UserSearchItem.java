package com.jipsamoye.backend.domain.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jipsamoye.backend.domain.user.entity.User;

public record UserSearchItem(
        String nickname,
        String profileImageUrl,
        @JsonProperty("isFollowing") boolean isFollowing
) {
    public static UserSearchItem of(User user, boolean isFollowing) {
        return new UserSearchItem(
                user.getNickname(),
                user.getProfileImageUrl(),
                isFollowing
        );
    }
}
