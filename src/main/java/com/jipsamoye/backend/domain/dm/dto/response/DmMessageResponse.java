package com.jipsamoye.backend.domain.dm.dto.response;

import com.jipsamoye.backend.domain.dm.entity.DmMessage;

import java.time.LocalDateTime;

public record DmMessageResponse(
        Long id,
        Long senderId,
        String senderNickname,
        String content,
        String imageUrl,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static DmMessageResponse from(DmMessage message) {
        return new DmMessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getNickname(),
                message.getContent(),
                message.getImageUrl(),
                message.getReadAt(),
                message.getCreatedAt()
        );
    }
}
