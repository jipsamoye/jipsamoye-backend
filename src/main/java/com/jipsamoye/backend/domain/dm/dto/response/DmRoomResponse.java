package com.jipsamoye.backend.domain.dm.dto.response;

import java.time.LocalDateTime;

public record DmRoomResponse(
        Long roomId,
        String otherUserNickname,
        String otherUserProfileImageUrl,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount
) {
}
