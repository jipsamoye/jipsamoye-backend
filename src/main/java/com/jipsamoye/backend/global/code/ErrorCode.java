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
    FIGURINE_JOB_NOT_COMPLETED(400, "FIGURINE_JOB_NOT_COMPLETED", "아직 완료되지 않은 생성 작업입니다."),

    // 401
    UNAUTHORIZED(401, "UNAUTHORIZED", "로그인이 필요합니다."),
    NAVER_TOKEN_EXCHANGE_FAILED(401, "NAVER_TOKEN_EXCHANGE_FAILED", "네이버 인증에 실패했습니다. 다시 로그인해주세요."),

    // 403
    FORBIDDEN(403, "FORBIDDEN", "권한이 없습니다."),

    // 404
    NOT_FOUND(404, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "유저를 찾을 수 없습니다."),
    POST_NOT_FOUND(404, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(404, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."),
    BOARD_NOT_FOUND(404, "BOARD_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    BOARD_COMMENT_NOT_FOUND(404, "BOARD_COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."),
    FIGURINE_JOB_NOT_FOUND(404, "FIGURINE_JOB_NOT_FOUND", "생성 작업을 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(405, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),

    // 406
    NOT_ACCEPTABLE(406, "NOT_ACCEPTABLE", "응답 형식을 맞출 수 없습니다."),

    // 409
    DUPLICATE_NICKNAME(409, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."),
    DUPLICATE_LIKE(409, "DUPLICATE_LIKE", "이미 좋아요한 게시글입니다."),
    FIGURINE_ALREADY_POSTED(409, "FIGURINE_ALREADY_POSTED", "이미 게시된 생성 작업입니다."),

    // 415
    UNSUPPORTED_MEDIA_TYPE(415, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다."),

    // 500
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),
    S3_UPLOAD_ERROR(500, "S3_UPLOAD_ERROR", "이미지 업로드에 실패했습니다."),

    // 502
    NAVER_API_ERROR(502, "NAVER_API_ERROR", "네이버 API 호출에 실패했습니다."),
    FIGURINE_GENERATION_FAILED(502, "FIGURINE_GENERATION_FAILED", "AI 이미지 생성에 실패했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
