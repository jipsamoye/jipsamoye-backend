package com.jipsamoye.backend.domain.notification.event;

import com.jipsamoye.backend.domain.notification.dto.response.NotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationCreatedEventListenerTest {

    @InjectMocks
    private NotificationCreatedEventListener listener;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private NotificationResponse payload() {
        return new NotificationResponse(
                1L, "LIKE", 10L, 20L, "좋아요 알림",
                "보낸이", "https://img.example.com/p.png", false, LocalDateTime.now());
    }

    @Test
    @DisplayName("이벤트 수신 시 receiver의 알림 토픽으로 payload를 전송한다")
    void handle_sendsPayloadToReceiver() {
        NotificationResponse payload = payload();

        listener.handle(new NotificationCreatedEvent(1L, payload));

        verify(messagingTemplate).convertAndSendToUser("1", "/sub/notifications", payload);
    }

    @Test
    @DisplayName("리스너는 AFTER_COMMIT 단계에서 실행된다 (전송 실패가 알림 저장을 롤백시키지 않음)")
    void handle_runsAfterCommit() throws Exception {
        Method handle = NotificationCreatedEventListener.class
                .getMethod("handle", NotificationCreatedEvent.class);
        TransactionalEventListener annotation = handle.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
