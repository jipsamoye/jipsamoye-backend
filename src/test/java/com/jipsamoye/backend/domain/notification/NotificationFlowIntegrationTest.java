package com.jipsamoye.backend.domain.notification;

import com.jipsamoye.backend.domain.notification.dto.response.NotificationResponse;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.notification.event.NotificationEvent;
import com.jipsamoye.backend.domain.notification.repository.NotificationRepository;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 알림 생성 → WS 전송의 트랜잭션 이벤트 배선 통합 테스트.
 *
 * <p>단위 테스트로는 검증할 수 없는 체인을 실제 스프링 컨텍스트로 확인한다:
 * 원 트랜잭션 커밋 → NotificationEventListener(AFTER_COMMIT + REQUIRES_NEW) → 알림 저장
 * → NotificationCreatedEvent 발행 → 내부 트랜잭션 커밋 → NotificationCreatedEventListener(AFTER_COMMIT)
 * → SimpMessagingTemplate 전송.
 *
 * <p>특히 "AFTER_COMMIT 리스너의 REQUIRES_NEW 트랜잭션 안에서 발행한 이벤트가
 * 그 내부 트랜잭션의 AFTER_COMMIT으로 발화되는지"와, WS 전송 실패가
 * 알림 저장을 롤백시키지 않는지(수정 목적)를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("contextload")
@DisplayName("알림 생성-전송 트랜잭션 이벤트 배선 통합 테스트")
class NotificationFlowIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private TransactionTemplate transactionTemplate;
    private User receiver;
    private User sender;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        receiver = newUser();
        sender = newUser();
    }

    private User newUser() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .nickname("u-" + unique)
                .email(unique + "@test.com")
                .provider(Provider.GOOGLE)
                .providerId("pid-" + unique)
                .role(Role.USER)
                .build());
    }

    private void publishInTransaction(Long targetId) {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new NotificationEvent(
                        receiver, sender, NotificationType.LIKE, targetId, targetId, "좋아요 알림")));
    }

    private boolean notificationExists(Long targetId) {
        return notificationRepository.existsBySenderAndReceiverAndTargetIdAndType(
                sender, receiver, targetId, NotificationType.LIKE);
    }

    @Test
    @DisplayName("원 트랜잭션 커밋 후 알림이 저장되고 receiver에게 WS 전송된다")
    void commit_savesNotificationAndSendsWebSocket() {
        publishInTransaction(100L);

        assertThat(notificationExists(100L)).isTrue();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(String.valueOf(receiver.getId())), eq("/sub/notifications"), payloadCaptor.capture());

        NotificationResponse payload = (NotificationResponse) payloadCaptor.getValue();
        assertThat(payload.type()).isEqualTo("LIKE");
        assertThat(payload.targetId()).isEqualTo(100L);
        assertThat(payload.message()).isEqualTo("좋아요 알림");
        assertThat(payload.senderNickname()).isEqualTo(sender.getNickname());
    }

    @Test
    @DisplayName("WS 전송이 실패해도 알림 저장은 롤백되지 않는다 (수정 전에는 유실)")
    void webSocketFailure_doesNotRollBackNotification() {
        doThrow(new RuntimeException("WS 전송 실패"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        try {
            publishInTransaction(200L);
        } catch (RuntimeException ignored) {
            // AFTER_COMMIT 리스너 예외는 커밋 이후에 전파될 수 있다 — 저장 결과만 검증한다.
        }

        assertThat(notificationExists(200L)).isTrue();
    }

    @Test
    @DisplayName("원 트랜잭션이 롤백되면 알림 저장도 WS 전송도 일어나지 않는다 (유령 알림 차단)")
    void rollback_noNotificationAndNoSend() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new NotificationEvent(
                    receiver, sender, NotificationType.LIKE, 300L, 300L, "좋아요 알림"));
            status.setRollbackOnly();
        });

        assertThat(notificationExists(300L)).isFalse();
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }
}
