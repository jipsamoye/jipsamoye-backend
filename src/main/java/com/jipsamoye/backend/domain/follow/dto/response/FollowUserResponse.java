package com.jipsamoye.backend.domain.follow.dto.response;

import com.jipsamoye.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FollowUserResponse {

    private Long id;
    private String nickname;
    private String profileImageUrl;

    public static FollowUserResponse from(User user) {
        return FollowUserResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
