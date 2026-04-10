package com.jipsamoye.backend.domain.user.service;

import com.jipsamoye.backend.domain.user.dto.request.UserUpdateRequest;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface UserService {

    UserResponse getProfile(String nickname);

    UserResponse updateProfile(Long userId, UserUpdateRequest request);

    PageResponse<PetPostListResponse> getUserPosts(String nickname, int page, int size);
}
