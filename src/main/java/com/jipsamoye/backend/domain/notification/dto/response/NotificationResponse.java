package com.jipsamoye.backend.domain.notification.dto.response;

import com.jipsamoye.backend.domain.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        Long targetId,
        String message,
        String senderNickname,
        String senderProfileImageUrl,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTargetId(),
                notification.getMessage(),
                notification.getSender().getNickname(),
                notification.getSender().getProfileImageUrl(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
