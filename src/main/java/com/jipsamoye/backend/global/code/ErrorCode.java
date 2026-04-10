package com.jipsamoye.backend.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    BAD_REQUEST(400, "BAD_REQUEST", "잘못된 요청입니다."),
    INVALID_INPUT(400, "INVALID_INPUT", "입력값이 유효하지 않습니다."),
    INVALID_FILE(400, "INVALID_FILE", "올바르지 않은 파일입니다."),
    MISSING_PARAMETER(400, "MISSING_PARAMETER", "필수 파라미터가 누락되었습니다."),

    // 401
    UNAUTHORIZED(401, "UNAUTHORIZED", "로그인이 필요합니다."),

    // 403
    FORBIDDEN(403, "FORBIDDEN", "권한이 없습니다."),

    // 404
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "유저를 찾을 수 없습니다."),
    POST_NOT_FOUND(404, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(404, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."),

    // 409
    DUPLICATE_NICKNAME(409, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."),
    DUPLICATE_LIKE(409, "DUPLICATE_LIKE", "이미 좋아요한 게시글입니다."),

    // 500
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),
    S3_UPLOAD_ERROR(500, "S3_UPLOAD_ERROR", "이미지 업로드에 실패했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
