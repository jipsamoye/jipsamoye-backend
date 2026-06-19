package com.jipsamoye.backend.global.util;

import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.Map;

/**
 * STOMP 메시지 핸들러에서 세션 attribute의 인증 userId를 안전하게 추출하는 공용 유틸.
 *
 * <p>핸드셰이크({@code WebSocketAuthInterceptor})가 인증에 성공하면 세션 attribute에
 * {@code "userId"}(Long)를 저장한다. 세션 만료/미인증 등으로 값이 없거나 타입이 어긋나면
 * {@link BusinessException}({@link ErrorCode#UNAUTHORIZED})을 던져 {@code WebSocketExceptionHandler}
 * 경로로 사용자에게 명시적인 에러를 전달한다. (가드 없이 null을 서비스로 넘기면 USER_NOT_FOUND로
 * 떨어져 원인이 모호해진다.)
 */
public final class WsSessionUtils {

    private static final String USER_ID_ATTRIBUTE = "userId";

    private WsSessionUtils() {
    }

    public static Long requireUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attributes = headerAccessor.getSessionAttributes();
        Object userId = attributes != null ? attributes.get(USER_ID_ATTRIBUTE) : null;
        if (userId instanceof Long longUserId) {
            return longUserId;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
