package com.jipsamoye.backend.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * 프론트가 /api/auth/me 호출 여부를 판단하기 위한 non-HttpOnly 힌트 쿠키.
 * 실제 세션(JSESSIONID)은 HttpOnly로 유지되며, 이 쿠키는 "로그인 상태 여부" 플래그만 노출한다.
 *
 * Max-Age는 설정하지 않아 session cookie로 동작한다. JSESSIONID가 기본적으로 session cookie인 것과
 * 수명을 맞춰 Spring Session의 rolling TTL과 힌트 쿠키 만료 시점이 어긋나는 문제를 방지한다.
 */
public final class SessionHintCookie {

    public static final String NAME = "has_session";
    private static final String VALUE = "1";
    private static final String PATH = "/";
    private static final String SAME_SITE = "Lax";

    private SessionHintCookie() {
    }

    public static void set(HttpServletResponse response, String domain, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(VALUE, null, domain, secure).toString());
    }

    public static void clear(HttpServletResponse response, String domain, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO, domain, secure).toString());
    }

    private static ResponseCookie build(String value, Duration maxAge, String domain, boolean secure) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, value)
                .path(PATH)
                .sameSite(SAME_SITE)
                .secure(secure)
                .httpOnly(false);
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }
}
