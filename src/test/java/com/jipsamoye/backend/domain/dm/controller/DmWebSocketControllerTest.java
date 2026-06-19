package com.jipsamoye.backend.domain.dm.controller;

import com.jipsamoye.backend.domain.dm.dto.request.DmReadRequest;
import com.jipsamoye.backend.domain.dm.dto.request.DmSendRequest;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageEvent;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.service.DmService;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        @DisplayName("전송 시 destination이 응답 roomId 기반 '/sub/dm/room/{roomId}'이고 envelope type=='MESSAGE', clientMessageId 에코 보존")
        void sendMessage_envelopeTypeAndClientMessageId() {
            DmSendRequest request = new DmSendRequest(42L, null, "안녕", null, "cid-001");
            DmMessageResponse serviceResponse = new DmMessageResponse(
                    1L, 42L, "발신자", "안녕", null, null, LocalDateTime.now());
            when(dmService.sendMessage(99L, 42L, null, "안녕", null)).thenReturn(serviceResponse);

            SimpMessageHeaderAccessor accessor = headerAccessorWithUserId(99L);
            controller.sendMessage(request, accessor);

            ArgumentCaptor<DmMessageEvent> eventCaptor = ArgumentCaptor.forClass(DmMessageEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/sub/dm/room/42"), eventCaptor.capture());

            DmMessageEvent event = eventCaptor.getValue();
            assertThat(event.type()).isEqualTo("MESSAGE");
            assertThat(event.message().clientMessageId()).isEqualTo("cid-001");
            assertThat(event.message().id()).isEqualTo(1L);
            assertThat(event.message().roomId()).isEqualTo(42L);
            assertThat(event.message().senderNickname()).isEqualTo("발신자");
        }

        @Test
        @DisplayName("clientMessageId가 null이어도 정상 동작하고 null이 에코됨")
        void sendMessage_nullClientMessageId_echoesNull() {
            DmSendRequest request = new DmSendRequest(7L, null, "내용", null, null);
            DmMessageResponse serviceResponse = new DmMessageResponse(
                    2L, 7L, "유저", "내용", null, null, LocalDateTime.now());
            when(dmService.sendMessage(5L, 7L, null, "내용", null)).thenReturn(serviceResponse);

            SimpMessageHeaderAccessor accessor = headerAccessorWithUserId(5L);
            controller.sendMessage(request, accessor);

            ArgumentCaptor<DmMessageEvent> eventCaptor = ArgumentCaptor.forClass(DmMessageEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/sub/dm/room/7"), eventCaptor.capture());

            DmMessageEvent event = eventCaptor.getValue();
            assertThat(event.type()).isEqualTo("MESSAGE");
            assertThat(event.message().clientMessageId()).isNull();
        }

        @Test
        @DisplayName("draft 전송(roomId null + targetNickname) 시 서비스가 resolve한 roomId 기반 destination으로 broadcast")
        void sendMessage_draft_broadcastsToResolvedRoomId() {
            DmSendRequest request = new DmSendRequest(null, "냥집사", "첫 메시지", null, "cid-draft");
            DmMessageResponse serviceResponse = new DmMessageResponse(
                    3L, 99L, "발신자", "첫 메시지", null, null, LocalDateTime.now());
            when(dmService.sendMessage(1L, null, "냥집사", "첫 메시지", null)).thenReturn(serviceResponse);

            SimpMessageHeaderAccessor accessor = headerAccessorWithUserId(1L);
            controller.sendMessage(request, accessor);

            ArgumentCaptor<DmMessageEvent> eventCaptor = ArgumentCaptor.forClass(DmMessageEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/sub/dm/room/99"), eventCaptor.capture());
            assertThat(eventCaptor.getValue().message().roomId()).isEqualTo(99L);
            assertThat(eventCaptor.getValue().message().clientMessageId()).isEqualTo("cid-draft");
        }

        @Test
        @DisplayName("세션 userId가 없으면 UNAUTHORIZED, 서비스·broadcast 미호출")
        void sendMessage_nullUserId_throwsUnauthorized() {
            DmSendRequest request = new DmSendRequest(1L, null, "안녕", null, "cid");
            SimpMessageHeaderAccessor accessor = headerAccessorWithUserId(null);

            assertThatThrownBy(() -> controller.sendMessage(request, accessor))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verifyNoInteractions(dmService);
            verifyNoInteractions(messagingTemplate);
        }
    }

    @Nested
    @DisplayName("read 메서드")
    class ReadTest {

        @Test
        @DisplayName("/dm/read 수신 시 세션 userId로 markAsRead(userId, roomId) 호출")
        void read_callsMarkAsRead() {
            DmReadRequest request = new DmReadRequest(55L);
            SimpMessageHeaderAccessor accessor = headerAccessorWithUserId(7L);

            controller.read(request, accessor);

            verify(dmService).markAsRead(7L, 55L);
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        @DisplayName("세션 userId가 없으면 UNAUTHORIZED, markAsRead 미호출")
        void read_nullUserId_throwsUnauthorized() {
            DmReadRequest request = new DmReadRequest(55L);
            SimpMessageHeaderAccessor accessor = headerAccessorWithUserId(null);

            assertThatThrownBy(() -> controller.read(request, accessor))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verifyNoInteractions(dmService);
        }
    }
}
