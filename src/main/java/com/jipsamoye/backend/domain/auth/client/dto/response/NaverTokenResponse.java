package com.jipsamoye.backend.domain.auth.client.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 네이버 토큰 교환 응답 DTO.
 * 네이버는 실패 시에도 HTTP 200 + error 필드를 반환하므로 {@link #isError()}로 구분한다.
 * {@code expires_in}은 문자열로 전달된다.
 */
public record NaverTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") String expiresIn,
        String error,
        @JsonProperty("error_description") String errorDescription
) {
    public boolean isError() {
        return error != null && !error.isBlank();
    }
}
