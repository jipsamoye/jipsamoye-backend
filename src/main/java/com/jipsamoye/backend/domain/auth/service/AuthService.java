package com.jipsamoye.backend.domain.auth.service;

import com.jipsamoye.backend.domain.user.dto.response.UserResponse;

public interface AuthService {

    /**
     * 둘러보기 임시 계정 생성 — UUID 기반 닉네임, GUEST role, 세션 발급
     */
    UserResponse createGuest();

    UserResponse getMe(Long userId);

    void logout();

    /**
     * 회원 탈퇴 — Like hard → Follow hard → Comment soft → PetPost soft → User soft
     */
    void withdraw(Long userId);
}
