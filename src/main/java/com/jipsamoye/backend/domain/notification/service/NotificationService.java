package com.jipsamoye.backend.domain.notification.service;

import com.jipsamoye.backend.domain.notification.dto.response.NotificationResponse;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.response.PageResponse;

public interface NotificationService {

    void send(User receiver, User sender, NotificationType type,
              Long targetId, Long relatedPostId, String message);

    PageResponse<NotificationResponse> getNotifications(Long userId, int page, int size);

    void markAsRead(Long notificationId, Long userId);

    long getUnreadCount(Long userId);

    void markAllAsRead(Long userId);
}
