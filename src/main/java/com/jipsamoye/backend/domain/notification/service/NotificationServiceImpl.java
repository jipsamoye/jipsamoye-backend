package com.jipsamoye.backend.domain.notification.service;

import com.jipsamoye.backend.domain.notification.dto.response.NotificationResponse;
import com.jipsamoye.backend.domain.notification.entity.Notification;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.notification.event.NotificationCreatedEvent;
import com.jipsamoye.backend.domain.notification.repository.NotificationRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void send(User receiver, User sender, NotificationType type,
                     Long targetId, Long relatedPostId, String message) {
        if (receiver.getId().equals(sender.getId())) return;
        if (notificationRepository.existsBySenderAndReceiverAndTargetIdAndType(sender, receiver, targetId, type)) return;

        Notification notification = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type(type)
                .targetId(targetId)
                .relatedPostId(relatedPostId)
                .message(message)
                .build();

        notificationRepository.save(notification);

        // WebSocket 전송은 커밋 후(AFTER_COMMIT 리스너)에 수행한다 —
        // 전송 실패가 알림 저장을 롤백시키거나, 커밋 전 전송으로 유령 알림이 생기지 않도록
        // payload는 LAZY 연관 접근이 가능한 트랜잭션 안에서 미리 만들어 담는다.
        eventPublisher.publishEvent(new NotificationCreatedEvent(
                receiver.getId(), NotificationResponse.from(notification)));
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
