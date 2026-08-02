package com.jipsamoye.backend.domain.notification.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 저장 트랜잭션 커밋 후 WebSocket으로 실시간 알림을 전송한다.
 * 트랜잭션 밖에서 실행되므로 전송 실패가 알림 저장을 롤백시키지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationCreatedEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.receiverId()),
                "/sub/notifications",
                event.payload());
    }
}
