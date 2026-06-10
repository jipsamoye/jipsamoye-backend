package com.jipsamoye.backend.domain.auth.client.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 네이버 프로필 조회 응답 DTO.
 * resultcode가 "00"이고 response가 null이 아닌 경우만 성공.
 */
public record NaverProfileResponse(
        String resultcode,
        String message,
        NaverProfile response
) {
    public boolean isSuccess() {
        return "00".equals(resultcode) && response != null;
    }

    public record NaverProfile(
            String id,
            String nickname,
            String email,
            @JsonProperty("profile_image") String profileImage
    ) {}
}
