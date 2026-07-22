package com.jipsamoye.backend.domain.chat.controller;

import com.jipsamoye.backend.domain.chat.dto.request.ChatSendRequest;
import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.service.ChatService;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatWebSocketController 단위 테스트")
class ChatWebSocketControllerTest {

    @InjectMocks
    private ChatWebSocketController controller;

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SimpMessageHeaderAccessor accessorWith(Object userId) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) {
            attrs.put("userId", userId);
        }
        when(accessor.getSessionAttributes()).thenReturn(attrs);
        return accessor;
    }

    @Test
    @DisplayName("전송 시 세션 userId로 메시지를 저장하고 /sub/chat/room으로 broadcast")
    void sendMessage_broadcasts() {
        ChatSendRequest request = new ChatSendRequest("안녕하세요");
        ChatMessageResponse response =
                new ChatMessageResponse("CHAT_MESSAGE", 1L, "닉", null, "안녕하세요", LocalDateTime.now());
        when(chatService.sendMessage(99L, "안녕하세요")).thenReturn(response);

        controller.sendMessage(request, accessorWith(99L));

        verify(chatService).sendMessage(99L, "안녕하세요");
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/room"), eq(response));
    }

    @Test
    @DisplayName("세션 userId가 없으면 UNAUTHORIZED를 던지고 서비스·broadcast 미호출")
    void sendMessage_nullUserId_throwsUnauthorized() {
        ChatSendRequest request = new ChatSendRequest("안녕하세요");

        assertThatThrownBy(() -> controller.sendMessage(request, accessorWith(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(chatService);
        verifyNoInteractions(messagingTemplate);
    }
}
