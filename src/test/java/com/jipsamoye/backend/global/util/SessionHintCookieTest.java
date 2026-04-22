package com.jipsamoye.backend.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionHintCookieTest {

    @Test
    @DisplayName("set - 힌트 쿠키 헤더에 name/value/domain/max-age/SameSite/Secure가 포함되고 HttpOnly는 없다")
    void set_producesExpectedCookieHeader() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionHintCookie.set(response, "jipsamoye.com", true, Duration.ofHours(2));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).startsWith("has_session=1");
        assertThat(header).contains("Path=/");
        assertThat(header).contains("Domain=jipsamoye.com");
        assertThat(header).contains("Max-Age=7200");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).contains("Secure");
        assertThat(header).doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("clear - Max-Age=0으로 즉시 삭제 Set-Cookie를 발급한다")
    void clear_producesDeletionCookieHeader() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionHintCookie.clear(response, "jipsamoye.com", true);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).startsWith("has_session=");
        assertThat(header).contains("Max-Age=0");
        assertThat(header).contains("Path=/");
        assertThat(header).contains("Domain=jipsamoye.com");
        assertThat(header).doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("set - 도메인이 비어있으면 Domain 속성을 빼고 발급한다 (로컬 환경 대응)")
    void set_omitsDomainWhenBlank() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionHintCookie.set(response, "", false, Duration.ofHours(2));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).startsWith("has_session=1");
        assertThat(header).doesNotContain("Domain=");
        assertThat(header).doesNotContain("Secure");
    }

    @Test
    @DisplayName("set - 도메인이 null이면 Domain 속성을 빼고 발급한다")
    void set_omitsDomainWhenNull() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionHintCookie.set(response, null, false, Duration.ofHours(2));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).doesNotContain("Domain=");
    }
}
