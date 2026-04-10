package com.jipsamoye.backend.domain.follow.service;

import com.jipsamoye.backend.domain.follow.dto.response.FollowUserResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface FollowService {

    boolean toggleFollow(String nickname, Long userId);

    PageResponse<FollowUserResponse> getFollowers(String nickname, int page, int size);

    PageResponse<FollowUserResponse> getFollowing(String nickname, int page, int size);
}
