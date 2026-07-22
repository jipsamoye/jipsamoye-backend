package com.jipsamoye.backend.global.exception;

import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.response.WsErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.security.Principal;

/**
 * STOMP {@code @MessageMapping} 핸들러에서 던진 예외를 사용자 전용 에러 채널로 전달한다.
 *
 * <p>{@code GlobalExceptionHandler}는 {@code @RestControllerAdvice}라 STOMP 경로엔 적용되지 않는다.
 * 그 결과 핸들러가 던진 {@link BusinessException}(USER_NOT_FOUND/FORBIDDEN/BAD_REQUEST 등)이나
 * {@code @Valid @Payload} 검증 실패가 <b>로그만 남고 클라이언트엔 전달되지 않아</b> 사용자는
 * "보냈는데 무반응" 상태가 된다. 이 어드바이스가 그 예외들을 잡아 REST {@code ApiResponse}와
 * 의미가 일관된 {@link WsErrorResponse} envelope로 변환해 전송한다.
 *
 * <p><b>전송 경로</b>: {@link SimpMessagingTemplate#convertAndSendToUser}로
 * {@code (principal.getName(), "/sub/errors", payload)} → 실제 destination {@code /user/sub/errors}.
 * Principal name은 핸드셰이크에서 설정한 userId 문자열이다. Principal이 없으면(미인증) 전송 대상이
 * 없으므로 로그만 남기고 종료한다.
 *
 * <p><b>프론트 계약(고정)</b>: 클라이언트는 {@code /user/sub/errors}를 구독해 {@code {code, message}}를 받는다.
 */
@Slf4j
@ControllerAdvice
public class WebSocketExceptionHandler {

    private static final String ERROR_DESTINATION = "/sub/errors";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * {@code SimpMessagingTemplate}을 {@code @Lazy}로 주입한다.
     *
     * <p>이 클래스는 {@code @ControllerAdvice}라 {@code @WebMvcTest} 슬라이스 컨텍스트도 함께 로드한다.
     * 그런데 MVC 슬라이스에는 WebSocket 인프라({@code SimpMessagingTemplate})가 없어 즉시 주입하면
     * 컨텍스트 로드가 깨진다. {@code @Lazy}는 시작 시 프록시만 주입하고 실제 빈 해석을 첫 호출로 미루므로,
     * STOMP 경로가 없는 슬라이스에서는 빈이 없어도 컨텍스트가 정상 로드된다(실 사용 시점엔 풀 컨텍스트라
     * 빈이 존재한다).
     */
    public WebSocketExceptionHandler(@Lazy SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * {@code @Valid @Payload} 검증 실패. 첫 위반 필드의 메시지를 사용자에게 그대로 전달한다.
     * (메시지가 없으면 INVALID_INPUT 기본 메시지.) 미포착 시 조용히 사라지므로 반드시 여기서 잡는다.
     */
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidation(MethodArgumentNotValidException e, Principal principal) {
        String detail = resolveFirstErrorMessage(e);
        log.warn("[WS-ERR] 검증 실패: principal={}, detail={}", principalName(principal), detail);
        send(principal, detail != null
                ? WsErrorResponse.of(ErrorCode.INVALID_INPUT, detail)
                : WsErrorResponse.of(ErrorCode.INVALID_INPUT));
    }

    @MessageExceptionHandler(BusinessException.class)
    public void handleBusiness(BusinessException e, Principal principal) {
        log.warn("[WS-ERR] BusinessException: principal={}, code={}, message={}",
                principalName(principal), e.getErrorCode().getCode(), e.getMessage());
        send(principal, WsErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    @MessageExceptionHandler(Exception.class)
    public void handleUnexpected(Exception e, Principal principal) {
        log.error("[WS-ERR] 처리되지 않은 STOMP 예외: principal={}", principalName(principal), e);
        send(principal, WsErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private void send(Principal principal, WsErrorResponse payload) {
        if (principal == null) {
            log.warn("[WS-ERR] Principal 없음 → 에러 전송 대상 없음. payload={}", payload);
            return;
        }
        messagingTemplate.convertAndSendToUser(principal.getName(), ERROR_DESTINATION, payload);
    }

    private String resolveFirstErrorMessage(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            return fieldError.getDefaultMessage();
        }
        if (e.getBindingResult().getGlobalError() != null) {
            return e.getBindingResult().getGlobalError().getDefaultMessage();
        }
        return null;
    }

    private String principalName(Principal principal) {
        return principal != null ? principal.getName() : "anonymous";
    }
}
