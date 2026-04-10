package com.jipsamoye.backend.domain.user.service;

import com.jipsamoye.backend.domain.user.dto.request.UserUpdateRequest;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface UserService {

    /**
     * 프로필 조회 — 탈퇴한 유저 접근 시 USER_NOT_FOUND(404)
     */
    UserResponse getProfile(String nickname);

    /**
     * 프로필 수정 — 닉네임 변경 시에만 중복 검증
     */
    UserResponse updateProfile(Long userId, UserUpdateRequest request);

    PageResponse<PetPostListResponse> getUserPosts(String nickname, int page, int size);
}
