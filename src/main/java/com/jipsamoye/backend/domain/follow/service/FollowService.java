package com.jipsamoye.backend.domain.follow.service;

import com.jipsamoye.backend.domain.follow.dto.response.FollowUserResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface FollowService {

    /**
     * 팔로우 토글 — 있으면 언팔(hard delete), 없으면 팔로우
     * 자기 자신 팔로우 시 BAD_REQUEST(400)
     * @return true: 팔로우, false: 언팔로우
     */
    boolean toggleFollow(String nickname, Long userId);

    PageResponse<FollowUserResponse> getFollowers(String nickname, int page, int size);

    PageResponse<FollowUserResponse> getFollowing(String nickname, int page, int size);
}
