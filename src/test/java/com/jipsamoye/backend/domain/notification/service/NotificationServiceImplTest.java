package com.jipsamoye.backend.domain.notification.service;

import com.jipsamoye.backend.domain.notification.entity.Notification;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.notification.event.NotificationCreatedEvent;
import com.jipsamoye.backend.domain.notification.repository.NotificationRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("전체 읽음 처리 - 성공")
    void markAllAsRead_success() {
        Long userId = 1L;
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        notificationService.markAllAsRead(userId);

        verify(notificationRepository).markAllAsRead(user);
    }

    @Test
    @DisplayName("전체 읽음 처리 - 존재하지 않는 유저")
    void markAllAsRead_userNotFound() {
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAllAsRead(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 전송 - 본인에게는 알림 안 보냄")
    void send_skipSelfNotification() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        notificationService.send(user, user, NotificationType.LIKE, 1L, 1L, "테스트");

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(eventPublisher, never()).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    @DisplayName("알림 전송 - 동일 sender+receiver+targetId+type 중복 시 알림 생성하지 않음")
    void send_skipDuplicateNotification() {
        User receiver = mock(User.class);
        User sender = mock(User.class);
        when(receiver.getId()).thenReturn(1L);
        when(sender.getId()).thenReturn(2L);
        when(notificationRepository.existsBySenderAndReceiverAndTargetIdAndType(sender, receiver, 10L, NotificationType.LIKE))
                .thenReturn(true);

        notificationService.send(receiver, sender, NotificationType.LIKE, 10L, 10L, "테스트");

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(eventPublisher, never()).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    @DisplayName("알림 전송 - 저장 후 WS 직접 전송 대신 NotificationCreatedEvent를 발행한다 (커밋 후 전송 분리)")
    void send_publishesEventInsteadOfDirectWebSocketSend() {
        User receiver = mock(User.class);
        User sender = mock(User.class);
        when(receiver.getId()).thenReturn(1L);
        when(sender.getId()).thenReturn(2L);
        when(sender.getNickname()).thenReturn("보낸이");
        when(notificationRepository.existsBySenderAndReceiverAndTargetIdAndType(sender, receiver, 10L, NotificationType.LIKE))
                .thenReturn(false);

        notificationService.send(receiver, sender, NotificationType.LIKE, 10L, 20L, "좋아요 알림");

        verify(notificationRepository).save(any(Notification.class));

        ArgumentCaptor<NotificationCreatedEvent> captor = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationCreatedEvent event = captor.getValue();
        assertThat(event.receiverId()).isEqualTo(1L);
        assertThat(event.payload().type()).isEqualTo("LIKE");
        assertThat(event.payload().targetId()).isEqualTo(10L);
        assertThat(event.payload().relatedPostId()).isEqualTo(20L);
        assertThat(event.payload().message()).isEqualTo("좋아요 알림");
        assertThat(event.payload().senderNickname()).isEqualTo("보낸이");
    }

    @Test
    @DisplayName("개별 읽음 처리 - 본인 알림이 아니면 FORBIDDEN")
    void markAsRead_forbidden() {
        Long notificationId = 1L;
        Long userId = 2L;

        User receiver = mock(User.class);
        when(receiver.getId()).thenReturn(1L);

        Notification notification = mock(Notification.class);
        when(notification.getReceiver()).thenReturn(receiver);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
