# Phase 2: 실시간 알림

**Goal:** WebSocket(STOMP)으로 실시간 알림 전송 + 알림 CRUD API

**Spec:** `docs/superpowers/specs/2026-04-14-social-features-backend-design.md`

**선행:** Phase 0 완료

---

### Task 2-1: NotificationType Enum 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/notification/entity/NotificationType.java`

- [ ] **Step 1: Enum 작성**

```java
package com.jipsamoye.backend.domain.notification.entity;

public enum NotificationType {
    LIKE,
    FOLLOW
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/notification/entity/NotificationType.java
git commit -m "feat: NotificationType enum 생성"
```

---

### Task 2-2: Notification 엔티티 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/notification/entity/Notification.java`

- [ ] **Step 1: 엔티티 작성**

```java
package com.jipsamoye.backend.domain.notification.entity;

import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean isRead = false;

    @Builder
    public Notification(User receiver, User sender, NotificationType type, Long targetId, String message) {
        this.receiver = receiver;
        this.sender = sender;
        this.type = type;
        this.targetId = targetId;
        this.message = message;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/notification/entity/Notification.java
git commit -m "feat: Notification 엔티티 생성"
```

---

### Task 2-3: NotificationRepository 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/notification/repository/NotificationRepository.java`

- [ ] **Step 1: Repository 작성**

```java
package com.jipsamoye.backend.domain.notification.repository;

import com.jipsamoye.backend.domain.notification.entity.Notification;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByReceiverOrderByCreatedAtDesc(User receiver, Pageable pageable);

    long countByReceiverAndIsReadFalse(User receiver);
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/notification/repository/NotificationRepository.java
git commit -m "feat: NotificationRepository 생성"
```

---

### Task 2-4: NotificationResponse 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/notification/dto/response/NotificationResponse.java`

- [ ] **Step 1: DTO 작성**

```java
package com.jipsamoye.backend.domain.notification.dto.response;

import com.jipsamoye.backend.domain.notification.entity.Notification;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {

    private Long id;
    private String type;
    private Long targetId;
    private String message;
    private Long senderId;
    private String senderNickname;
    private String senderProfileImageUrl;
    private boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .targetId(notification.getTargetId())
                .message(notification.getMessage())
                .senderId(notification.getSender().getId())
                .senderNickname(notification.getSender().getNickname())
                .senderProfileImageUrl(notification.getSender().getProfileImageUrl())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/notification/dto/response/NotificationResponse.java
git commit -m "feat: NotificationResponse DTO 생성"
```

---

### Task 2-5: NotificationService 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/notification/service/NotificationService.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/notification/service/NotificationServiceImpl.java`

- [ ] **Step 1: 인터페이스 작성**

```java
package com.jipsamoye.backend.domain.notification.service;

import com.jipsamoye.backend.domain.notification.dto.response.NotificationResponse;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.response.PageResponse;

public interface NotificationService {

    void send(User receiver, User sender, NotificationType type, Long targetId, String message);

    PageResponse<NotificationResponse> getNotifications(Long userId, int page, int size);

    void markAsRead(Long notificationId, Long userId);

    long getUnreadCount(Long userId);
}
```

- [ ] **Step 2: Impl 작성 (WebSocket 전송)**

```java
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
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/notification/service/
git commit -m "feat: NotificationService 생성 (WebSocket 알림 전송)"
```

---

### Task 2-6: NotificationController 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/notification/controller/NotificationController.java`

- [ ] **Step 1: 컨트롤러 작성**

```java
package com.jipsamoye.backend.domain.notification.controller;

import com.jipsamoye.backend.domain.notification.dto.response.NotificationResponse;
import com.jipsamoye.backend.domain.notification.service.NotificationService;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotifications(userId, page, size)));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @RequestParam Long userId) {
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success("알림 읽음 처리 완료"));
    }

    @Operation(summary = "미읽은 알림 수")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount(userId)));
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/notification/controller/NotificationController.java
git commit -m "feat: 알림 API 컨트롤러 생성"
```

---

### Task 2-7: 기존 서비스에 알림 연동

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/like/service/LikeServiceImpl.java`
- Modify: `src/main/java/com/jipsamoye/backend/domain/follow/service/FollowServiceImpl.java`

- [ ] **Step 1: LikeServiceImpl — 좋아요 시 알림**

NotificationService 주입 후 toggleLike에서 좋아요 추가 시:
```java
notificationService.send(
    petPost.getUser(), user,
    NotificationType.LIKE, postId,
    user.getNickname() + "님이 게시글에 좋아요를 눌렀습니다");
```

- [ ] **Step 2: FollowServiceImpl — 팔로우 시 알림**

toggleFollow에서 팔로우 추가 시:
```java
notificationService.send(
    following, follower,
    NotificationType.FOLLOW, follower.getId(),
    follower.getNickname() + "님이 회원님을 팔로우했습니다");
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/like/service/LikeServiceImpl.java
git add src/main/java/com/jipsamoye/backend/domain/follow/service/FollowServiceImpl.java
git commit -m "feat: 좋아요/팔로우 시 알림 발송 연동"
```

---

### Task 2-8: Phase 2 배포

- [ ] **Step 1: feature → develop → main PR/머지**
- [ ] **Step 2: feature 브랜치 삭제, develop 최신 동기화**
