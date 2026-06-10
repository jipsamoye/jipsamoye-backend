package com.jipsamoye.backend.domain.dm.event;

import com.jipsamoye.backend.domain.dm.dto.response.DmReadEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DmReadEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DmMessagesReadEvent event) {
        messagingTemplate.convertAndSend(
                "/sub/dm/room/" + event.roomId(),
                DmReadEvent.of(event.readerNickname(), event.readAt())
        );
    }
}
