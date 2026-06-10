package com.jipsamoye.backend.domain.dm.event;

import java.time.LocalDateTime;

public record DmMessagesReadEvent(
        Long roomId,
        String readerNickname,
        LocalDateTime readAt
) {
}
