package com.jipsamoye.backend.global.exception;

import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.response.WsErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;

import java.security.Principal;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketExceptionHandler 단위 테스트")
class WebSocketExceptionHandlerTest {

    @InjectMocks
    private WebSocketExceptionHandler handler;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private Principal principal(String name) {
        return () -> name;
    }

    /** 임의 메서드의 MethodParameter — MethodArgumentNotValidException 생성에 필요(내용은 무관). */
    @SuppressWarnings("unused")
    void dummyHandler(String payload) {
    }

    private MethodParameter dummyParameter() throws NoSuchMethodException {
        return new MethodParameter(
                WebSocketExceptionHandlerTest.class.getDeclaredMethod("dummyHandler", String.class), 0);
    }

    @Test
    @DisplayName("BusinessException → 해당 code/message로 /user/sub/errors 전송")
    void handleBusiness_sendsErrorEnvelope() {
        BusinessException e = new BusinessException(ErrorCode.FORBIDDEN, "권한이 없습니다.");

        handler.handleBusiness(e, principal("42"));

        ArgumentCaptor<WsErrorResponse> captor = ArgumentCaptor.forClass(WsErrorResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq("42"), eq("/sub/errors"), captor.capture());
        assertThat(captor.getValue().code()).isEqualTo("FORBIDDEN");
        assertThat(captor.getValue().message()).isEqualTo("권한이 없습니다.");
    }

    @Test
    @DisplayName("검증 실패(MethodArgumentNotValidException) → INVALID_INPUT + 위반 메시지 전송")
    void handleValidation_sendsFirstFieldMessage() throws Exception {
        BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "chatSendRequest");
        bindingResult.addError(new FieldError(
                "chatSendRequest", "content", null, false,
                new String[]{"NotBlank"}, null, "메시지 내용은 비어 있을 수 없습니다."));
        MethodArgumentNotValidException e = new MethodArgumentNotValidException(
                MessageBuilder.withPayload("x").build(), dummyParameter(), bindingResult);

        handler.handleValidation(e, principal("7"));

        ArgumentCaptor<WsErrorResponse> captor = ArgumentCaptor.forClass(WsErrorResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq("7"), eq("/sub/errors"), captor.capture());
        assertThat(captor.getValue().code()).isEqualTo(ErrorCode.INVALID_INPUT.getCode());
        assertThat(captor.getValue().message()).isEqualTo("메시지 내용은 비어 있을 수 없습니다.");
    }

    @Test
    @DisplayName("검증 실패에 필드 메시지가 없으면 INVALID_INPUT 기본 메시지 전송")
    void handleValidation_noFieldMessage_usesDefault() throws Exception {
        BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "chatSendRequest");
        MethodArgumentNotValidException e = new MethodArgumentNotValidException(
                MessageBuilder.withPayload("x").build(), dummyParameter(), bindingResult);

        handler.handleValidation(e, principal("7"));

        ArgumentCaptor<WsErrorResponse> captor = ArgumentCaptor.forClass(WsErrorResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq("7"), eq("/sub/errors"), captor.capture());
        assertThat(captor.getValue().code()).isEqualTo(ErrorCode.INVALID_INPUT.getCode());
        assertThat(captor.getValue().message()).isEqualTo(ErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("그 외 예외 → INTERNAL_SERVER_ERROR 전송")
    void handleUnexpected_sendsInternalError() {
        handler.handleUnexpected(new IllegalStateException("boom"), principal("9"));

        ArgumentCaptor<WsErrorResponse> captor = ArgumentCaptor.forClass(WsErrorResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq("9"), eq("/sub/errors"), captor.capture());
        assertThat(captor.getValue().code()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    }

    @Test
    @DisplayName("Principal이 null이면 전송하지 않음(전송 대상 없음)")
    void noPrincipal_doesNotSend() {
        handler.handleBusiness(new BusinessException(ErrorCode.BAD_REQUEST), null);

        verifyNoInteractions(messagingTemplate);
    }
}
