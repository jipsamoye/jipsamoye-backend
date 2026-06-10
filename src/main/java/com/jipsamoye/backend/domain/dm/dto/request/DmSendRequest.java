package com.jipsamoye.backend.domain.dm.dto.request;

public record DmSendRequest(
        Long roomId,
        String content,
        String imageUrl,
        String clientMessageId
) {
}
