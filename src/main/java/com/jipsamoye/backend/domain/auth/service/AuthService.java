package com.jipsamoye.backend.domain.auth.service;

import com.jipsamoye.backend.domain.auth.dto.request.NaverLoginRequest;
import com.jipsamoye.backend.domain.auth.dto.response.NaverLoginResponse;
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

    /**
     * 네이버 소셜 로그인 — 인가 코드로 토큰 교환 후 프로필 조회, 신규면 가입 처리, 세션 발급.
     * state CSRF 검증 책임은 프론트엔드에 있으며, 백엔드는 state를 토큰 요청에 전달만 한다.
     */
    NaverLoginResponse naverLogin(NaverLoginRequest request);
}
