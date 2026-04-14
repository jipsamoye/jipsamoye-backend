package com.jipsamoye.backend.domain.notification.service;

import com.jipsamoye.backend.domain.notification.dto.response.NotificationResponse;
import com.jipsamoye.backend.domain.notification.entity.Notification;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.notification.repository.NotificationRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void send(User receiver, User sender, NotificationType type, Long targetId, String message) {
        if (receiver.getId().equals(sender.getId())) return;
        if (notificationRepository.existsBySenderAndTargetIdAndType(sender, targetId, type)) return;

        Notification notification = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type(type)
                .targetId(targetId)
                .message(message)
                .build();

        notificationRepository.save(notification);

        messagingTemplate.convertAndSend(
                "/sub/notifications/" + receiver.getId(),
                NotificationResponse.from(notification));
    }

    @Override
    public PageResponse<NotificationResponse> getNotifications(Long userId, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Page<NotificationResponse> notificationPage = notificationRepository
                .findAllByReceiverOrderByCreatedAtDesc(user, PageRequest.of(page, size))
                .map(NotificationResponse::from);
        return PageResponse.from(notificationPage);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "알림을 찾을 수 없습니다."));

        if (!notification.getReceiver().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        notification.markAsRead();
    }

    @Override
    public long getUnreadCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return notificationRepository.countByReceiverAndIsReadFalse(user);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        notificationRepository.markAllAsRead(user);
    }
}
