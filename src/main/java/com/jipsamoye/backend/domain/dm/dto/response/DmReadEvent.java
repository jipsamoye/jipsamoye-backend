package com.jipsamoye.backend.domain.dm.dto.response;

import java.time.LocalDateTime;

public record DmReadEvent(
        String type,
        String readerNickname,
        LocalDateTime readAt
) {
    public static DmReadEvent of(String readerNickname, LocalDateTime readAt) {
        return new DmReadEvent("READ", readerNickname, readAt);
    }
}
