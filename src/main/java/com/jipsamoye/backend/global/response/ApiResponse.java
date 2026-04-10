package com.jipsamoye.backend.global.response;

import com.jipsamoye.backend.global.code.ErrorCode;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final int status;
    private final String code;
    private final String message;
    private final T data;

    @Builder
    private ApiResponse(int status, String code, String message, T data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ===== 성공 =====

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .code("SUCCESS")
                .message("요청 성공")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .code("SUCCESS")
                .message(message)
                .data(data)
                .build();
    }

    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .status(200)
                .code("SUCCESS")
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .status(201)
                .code("CREATED")
                .message("생성 성공")
                .data(data)
                .build();
    }

    // ===== 에러 =====

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return ApiResponse.<Void>builder()
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return ApiResponse.<Void>builder()
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return ApiResponse.<T>builder()
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .data(data)
                .build();
    }
}
