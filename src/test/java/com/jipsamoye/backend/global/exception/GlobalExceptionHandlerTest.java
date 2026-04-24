package com.jipsamoye.backend.global.exception;

import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 봇 스캔 등으로 빈번히 발생하는 HTTP 수준 오류가 500 + Discord 알림이 아닌
 * 4xx + WARN 로그로 조용히 처리되는지 검증.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("POST / 같이 경로는 있지만 메서드가 안 맞을 때 → 405 Method Not Allowed")
    void handleMethodNotAllowed() {
        HttpRequestMethodNotSupportedException e =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotAllowed(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThat(response.getBody().getMessage()).contains("지원하지 않는 HTTP 메서드");
    }

    @Test
    @DisplayName("요청 Content-Type이 컨트롤러와 안 맞을 때 → 415 Unsupported Media Type")
    void handleMediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException e =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_XML, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ApiResponse<Void>> response = handler.handleMediaTypeNotSupported(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Content-Type");
    }

    @Test
    @DisplayName("Accept 헤더로 돌려줄 표현이 없을 때 → 406 Not Acceptable")
    void handleMediaTypeNotAcceptable() {
        HttpMediaTypeNotAcceptableException e =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ApiResponse<Void>> response = handler.handleMediaTypeNotAcceptable(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("응답 형식");
    }

    @Test
    @DisplayName("진짜 서버 버그(RuntimeException)는 기존대로 500 반환 + catch-all에 떨어진다")
    void handleUnexpected_stillReachesCatchAll() {
        RuntimeException e = new RuntimeException("DB 커넥션 풀 고갈 같은 진짜 버그");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    }
}
