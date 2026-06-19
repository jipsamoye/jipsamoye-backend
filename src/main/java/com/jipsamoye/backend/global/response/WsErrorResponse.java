package com.jipsamoye.backend.global.response;

import com.jipsamoye.backend.global.code.ErrorCode;

/**
 * WebSocket(STOMP) 에러 채널({@code /user/sub/errors}) 전송용 표준 에러 envelope.
 *
 * <p>REST의 {@link ApiResponse} 에러와 의미를 맞추되(코드·메시지), WS 경로에서는 HTTP status·data가
 * 불필요하므로 {@code code}/{@code message} 두 필드로 슬림화한다. STOMP 핸들러가 던지는 예외를
 * {@code WebSocketExceptionHandler}가 잡아 이 형식으로 사용자 전용 채널에 전달한다.
 *
 * <p><b>프론트 계약(고정)</b>: 클라이언트는 {@code /user/sub/errors}를 구독해 {@code {code, message}}를 수신한다.
 */
public record WsErrorResponse(
        String code,
        String message
) {
    public static WsErrorResponse of(ErrorCode errorCode) {
        return new WsErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }

    public static WsErrorResponse of(ErrorCode errorCode, String message) {
        return new WsErrorResponse(errorCode.getCode(), message);
    }
}
