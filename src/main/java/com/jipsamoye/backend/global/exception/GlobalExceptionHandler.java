package com.jipsamoye.backend.global.exception;

import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        log.warn("Validation failed: {}", e.getMessage());
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "",
                        (existing, replacement) -> existing
                ));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        log.warn("Missing parameter: {}", e.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.MISSING_PARAMETER, e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPartException(
            MissingServletRequestPartException e) {
        log.warn("Missing request part: {}", e.getRequestPartName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.MISSING_PARAMETER, e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        log.warn("Message not readable: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("No resource found: {}", e.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    protected ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("No handler found: {}", e.getRequestURL());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "요청한 리소스를 찾을 수 없습니다."));
    }

    // 경로는 있는데 HTTP 메서드가 허용 안 된 경우 (예: POST / — GET / 만 매핑됨).
    // Unhandled 로 catch-all 에 떨어지면 500 + Discord 알림 발사되므로 별도 처리.
    // 봇 스캐너의 공격적 스캔 요청이 대부분 이 경로로 들어옴.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {} (supported: {})", e.getMethod(), e.getSupportedHttpMethods());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "지원하지 않는 HTTP 메서드입니다."));
    }

    // 요청 Content-Type 이 컨트롤러가 받아들이는 타입이 아닐 때.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("Media type not supported: {}", e.getContentType());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "지원하지 않는 Content-Type입니다."));
    }

    // 요청 Accept 헤더로 돌려줄 수 있는 표현이 없을 때.
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        log.warn("Media type not acceptable: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "응답 형식을 맞출 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
