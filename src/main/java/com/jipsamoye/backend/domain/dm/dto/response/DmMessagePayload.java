package com.jipsamoye.backend.domain.dm.dto.response;

import java.time.LocalDateTime;

public record DmMessagePayload(
        Long id,
        String senderNickname,
        String content,
        String imageUrl,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        String clientMessageId
) {
    public static DmMessagePayload of(DmMessageResponse r, String clientMessageId) {
        return new DmMessagePayload(
                r.id(),
                r.senderNickname(),
                r.content(),
                r.imageUrl(),
                r.readAt(),
                r.createdAt(),
                clientMessageId
        );
    }
}
