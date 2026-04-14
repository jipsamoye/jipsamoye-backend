package com.jipsamoye.backend.domain.chat.event;

import com.jipsamoye.backend.domain.user.event.ProfileUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatProfileEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProfileUpdate(ProfileUpdatedEvent event) {
        messagingTemplate.convertAndSend("/sub/chat/room", Map.of(
                "type", "PROFILE_UPDATED",
                "userId", event.getUserId(),
                "nickname", event.getNickname(),
                "profileImageUrl", event.getProfileImageUrl() != null ? event.getProfileImageUrl() : ""
        ));
    }
}
