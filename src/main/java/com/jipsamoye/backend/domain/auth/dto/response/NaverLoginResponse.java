package com.jipsamoye.backend.domain.auth.dto.response;

import com.jipsamoye.backend.domain.user.dto.response.UserResponse;

/**
 * 네이버 소셜 로그인 응답 DTO.
 *
 * @param isNewUser 신규 가입 여부 (true: 이번 로그인에서 처음 가입, false: 기존 회원)
 * @param user      로그인된 유저 정보
 */
public record NaverLoginResponse(
        boolean isNewUser,
        UserResponse user
) {
    public static NaverLoginResponse of(boolean isNewUser, UserResponse user) {
        return new NaverLoginResponse(isNewUser, user);
    }
}
