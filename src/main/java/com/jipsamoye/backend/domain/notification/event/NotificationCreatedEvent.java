package com.jipsamoye.backend.domain.notification.event;

import com.jipsamoye.backend.domain.notification.dto.response.NotificationResponse;

/**
 * 알림이 저장된 후 WebSocket 실시간 전송을 위해 발행되는 이벤트.
 * 커밋 후 리스너에서 LAZY 연관을 건드리지 않도록 완성된 payload(DTO)를 담는다.
 */
public record NotificationCreatedEvent(Long receiverId, NotificationResponse payload) {
}
