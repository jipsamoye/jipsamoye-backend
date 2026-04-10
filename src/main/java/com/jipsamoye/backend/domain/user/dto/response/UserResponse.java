package com.jipsamoye.backend.domain.user.dto.response;

import com.jipsamoye.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {

    private Long id;
    private String nickname;
    private String bio;
    private String profileImageUrl;
    private long postCount;
    private long followerCount;
    private long followingCount;
    private LocalDateTime createdAt;

    public static UserResponse of(User user, long postCount, long followerCount, long followingCount) {
        return UserResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .bio(user.getBio())
                .profileImageUrl(user.getProfileImageUrl())
                .postCount(postCount)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
