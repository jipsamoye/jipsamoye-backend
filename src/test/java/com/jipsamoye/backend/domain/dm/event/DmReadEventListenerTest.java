package com.jipsamoye.backend.domain.dm.event;

import com.jipsamoye.backend.domain.dm.dto.response.DmReadEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DmReadEventListener 단위 테스트")
class DmReadEventListenerTest {

    @InjectMocks
    private DmReadEventListener listener;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("DmMessagesReadEvent 수신 시 '/sub/dm/room/{roomId}'에 type=='READ', readerNickname/readAt 전송")
    void handle_publishesReadEvent() {
        LocalDateTime readAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        DmMessagesReadEvent event = new DmMessagesReadEvent(5L, "냥집사", readAt);

        listener.handle(event);

        ArgumentCaptor<DmReadEvent> captor = ArgumentCaptor.forClass(DmReadEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/dm/room/5"), captor.capture());

        DmReadEvent readEvent = captor.getValue();
        assertThat(readEvent.type()).isEqualTo("READ");
        assertThat(readEvent.readerNickname()).isEqualTo("냥집사");
        assertThat(readEvent.readAt()).isEqualTo(readAt);
    }
}
