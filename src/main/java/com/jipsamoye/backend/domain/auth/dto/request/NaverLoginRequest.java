package com.jipsamoye.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 네이버 소셜 로그인 요청 DTO.
 * 프론트엔드가 네이버 인가 코드 플로우 후 전달하는 code/state 쌍.
 */
public record NaverLoginRequest(
        @NotBlank String code,
        @NotBlank String state
) {}
