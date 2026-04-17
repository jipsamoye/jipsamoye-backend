package com.jipsamoye.backend.domain.dm.dto.request;

public record DmSendRequest(
        Long userId,
        Long roomId,
        String content,
        String imageUrl
) {
}
