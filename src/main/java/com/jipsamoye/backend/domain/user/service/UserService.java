package com.jipsamoye.backend.domain.user.service;

import com.jipsamoye.backend.domain.user.dto.request.UserUpdateRequest;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.domain.user.dto.response.UserSearchItem;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface UserService {

    /**
     * 프로필 조회 — 탈퇴한 유저 접근 시 USER_NOT_FOUND(404)
     * currentUserId가 null이면 비로그인으로 처리 (isFollowing = false)
     */
    UserResponse getProfile(String nickname, Long currentUserId);

    /**
     * 프로필 수정 — 닉네임 변경 시에만 중복 검증
     */
    UserResponse updateProfile(Long userId, UserUpdateRequest request);

    PageResponse<PetPostListResponse> getUserPosts(String nickname, int page, int size);

    boolean isNicknameAvailable(String nickname);

    /**
     * 닉네임 부분일치 유저 검색 — 본인·탈퇴 유저 제외, 정확>접두>부분 정렬.
     * q가 비어 있으면 빈 결과를 반환한다.
     * currentUserId가 null(비로그인)이면 isFollowing은 전부 false.
     */
    PageResponse<UserSearchItem> searchUsers(String q, Long currentUserId, int page, int size);
}
