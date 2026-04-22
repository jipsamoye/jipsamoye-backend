package com.jipsamoye.backend.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionHintCookieTest {

    @Test
    @DisplayName("set - session cookie(Max-Age 없음)로 발급되고 HttpOnly도 없다")
    void set_producesSessionCookieHeader() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionHintCookie.set(response, "jipsamoye.com", true);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).startsWith("has_session=1");
        assertThat(header).contains("Path=/");
        assertThat(header).contains("Domain=jipsamoye.com");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).contains("Secure");
        // Max-Age 없음: session cookie로 동작해 브라우저 종료 시 삭제됨
        // (JSESSIONID와 수명 일치 → Spring Session rolling TTL과 어긋남 방지)
        assertThat(header).doesNotContain("Max-Age");
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

        SessionHintCookie.set(response, "", false);

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

        SessionHintCookie.set(response, null, false);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).doesNotContain("Domain=");
    }
}
