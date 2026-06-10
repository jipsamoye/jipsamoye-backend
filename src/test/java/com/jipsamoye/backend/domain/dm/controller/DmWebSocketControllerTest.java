package com.jipsamoye.backend.domain.dm.controller;

import com.jipsamoye.backend.domain.dm.dto.request.DmSendRequest;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageEvent;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.service.DmService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DmWebSocketController 단위 테스트")
class DmWebSocketControllerTest {

    @InjectMocks
    private DmWebSocketController controller;

    @Mock
    private DmService dmService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SimpMessageHeaderAccessor headerAccessorWithUserId(Long userId) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);
        return accessor;
    }

    @Nested
    @DisplayName("sendMessage 메서드")
    class SendMessageTest {

        @Test
        @DisplayName("전송 시 destination이 '/sub/dm/room/{roomId}'이고 envelope type=='MESSAGE', clientMessageId 에코 보존")
        void sendMessage_envelopeTypeAndClientMessageId() {
            DmSendRequest request = new DmSendRequest(42L, "안녕", null, "cid-001");
            DmMessageResponse serviceResponse = new DmMessageResponse(
                    1L, "발신자", "안녕", null, null, LocalDateTime.now());
            when(dmService.sendMessage(99L, 42L, "안녕", null)).thenReturn(serviceResponse);

            SimpMessageHeaderAccessor accessor = headerAccessorWithUserId(99L);
            controller.sendMessage(request, accessor);

            ArgumentCaptor<DmMessageEvent> eventCaptor = ArgumentCaptor.forClass(DmMessageEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/sub/dm/room/42"), eventCaptor.capture());

            DmMessageEvent event = eventCaptor.getValue();
            assertThat(event.type()).isEqualTo("MESSAGE");
            assertThat(event.message().clientMessageId()).isEqualTo("cid-001");
            assertThat(event.message().id()).isEqualTo(1L);
            assertThat(event.message().senderNickname()).isEqualTo("발신자");
        }

        @Test
        @DisplayName("clientMessageId가 null이어도 정상 동작하고 null이 에코됨")
        void sendMessage_nullClientMessageId_echoesNull() {
            DmSendRequest request = new DmSendRequest(7L, "내용", null, null);
            DmMessageResponse serviceResponse = new DmMessageResponse(
                    2L, "유저", "내용", null, null, LocalDateTime.now());
            when(dmService.sendMessage(5L, 7L, "내용", null)).thenReturn(serviceResponse);

            SimpMessageHeaderAccessor accessor = headerAccessorWithUserId(5L);
            controller.sendMessage(request, accessor);

            ArgumentCaptor<DmMessageEvent> eventCaptor = ArgumentCaptor.forClass(DmMessageEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/sub/dm/room/7"), eventCaptor.capture());

            DmMessageEvent event = eventCaptor.getValue();
            assertThat(event.type()).isEqualTo("MESSAGE");
            assertThat(event.message().clientMessageId()).isNull();
        }
    }
}
