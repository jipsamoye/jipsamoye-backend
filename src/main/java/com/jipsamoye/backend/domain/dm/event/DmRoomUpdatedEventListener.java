package com.jipsamoye.backend.domain.dm.event;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DmRoomUpdatedEventListener {

    private final DmRoomRepository dmRoomRepository;
    private final DmMessageRepository dmMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DmRoomUpdatedEvent event) {
        Optional<DmRoom> roomOpt = dmRoomRepository.findById(event.roomId());
        if (roomOpt.isEmpty()) {
            return;
        }
        DmRoom room = roomOpt.get();
        Optional<DmMessage> lastMsg = dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(room);

        for (Long targetUserId : event.targetUserIds()) {
            Optional<User> targetOpt = userRepository.findById(targetUserId);
            if (targetOpt.isEmpty()) {
                continue;
            }
            User target = targetOpt.get();
            if (!room.isParticipant(targetUserId)) {
                continue;
            }

            User other = room.getUser1().getId().equals(targetUserId)
                    ? room.getUser2()
                    : room.getUser1();
            long unread = dmMessageRepository.countUnread(room, target);

            DmRoomResponse payload = new DmRoomResponse(
                    room.getId(),
                    other.getNickname(),
                    other.getProfileImageUrl(),
                    lastMsg.map(DmMessage::getContent).orElse(null),
                    lastMsg.map(DmMessage::getCreatedAt).orElse(null),
                    unread
            );

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(targetUserId),
                    "/sub/dm/rooms",
                    payload
            );
        }
    }
}
