package com.jipsamoye.backend.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * 프론트가 /api/auth/me 호출 여부를 판단하기 위한 non-HttpOnly 힌트 쿠키.
 * 실제 세션(JSESSIONID)은 HttpOnly로 유지되며, 이 쿠키는 "로그인 상태 여부" 플래그만 노출한다.
 */
public final class SessionHintCookie {

    public static final String NAME = "has_session";
    private static final String VALUE = "1";
    private static final String PATH = "/";
    private static final String SAME_SITE = "Lax";

    private SessionHintCookie() {
    }

    public static void set(HttpServletResponse response, String domain, boolean secure, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(VALUE, maxAge, domain, secure).toString());
    }

    public static void clear(HttpServletResponse response, String domain, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO, domain, secure).toString());
    }

    private static ResponseCookie build(String value, Duration maxAge, String domain, boolean secure) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, value)
                .path(PATH)
                .maxAge(maxAge)
                .sameSite(SAME_SITE)
                .secure(secure)
                .httpOnly(false);
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }
}
