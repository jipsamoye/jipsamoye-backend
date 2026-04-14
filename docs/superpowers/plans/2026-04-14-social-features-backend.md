# 소셜 기능 고도화 백엔드 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 프론트엔드 소셜 기능 고도화에 맞는 백엔드 API 구현 (대댓글, 알림, 오픈채팅, DM)

**Architecture:** Phase별 독립 구현. WebSocket(STOMP)으로 알림/채팅/DM 실시간 통신 통합. 기존 도메인 패턴(Controller → Service(인터페이스+Impl) → Repository → Entity) 유지.

**Tech Stack:** Spring Boot 3.5.13, Java 17, MySQL 8.0, WebSocket + STOMP, Spring @Async

**Spec:** `docs/superpowers/specs/2026-04-14-social-features-backend-design.md`

---

## Phase 0: 공통 설정

### Task 0-1: OSIV 비활성화

**Files:**
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: application.yaml에 OSIV 설정 추가**

```yaml
spring:
  jpa:
    open-in-view: false
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/application.yaml
git commit -m "chore: OSIV 비활성화 (open-in-view: false)"
```

### Task 0-2: WebSocket + STOMP 의존성 추가

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: build.gradle에 websocket 의존성 추가**

```groovy
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add build.gradle
git commit -m "chore: WebSocket 의존성 추가"
```

### Task 0-3: WebSocketConfig 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/global/config/WebSocketConfig.java`

- [ ] **Step 1: WebSocketConfig 작성**

```java
package com.jipsamoye.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/sub");
        config.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/global/config/WebSocketConfig.java
git commit -m "feat: WebSocket + STOMP 설정 추가"
```

### Task 0-4: AsyncConfig 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/global/config/AsyncConfig.java`

- [ ] **Step 1: AsyncConfig 작성**

```java
package com.jipsamoye.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/global/config/AsyncConfig.java
git commit -m "feat: @Async 설정 추가"
```

---

## Phase 1: 대댓글

### Task 1-1: Comment 엔티티 수정

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/entity/Comment.java`

- [ ] **Step 1: @SQLRestriction 제거, parent 필드 추가**

```java
// @SQLRestriction("deleted_at IS NULL") ← 이 줄 제거

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id")
private Comment parent;
```

Builder에도 parent 추가:

```java
@Builder
public Comment(PetPost petPost, User user, String content, Comment parent) {
    this.petPost = petPost;
    this.user = user;
    this.content = content;
    this.parent = parent;
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/comment/entity/Comment.java
git commit -m "feat: Comment 엔티티에 parent 자기참조 추가, @SQLRestriction 제거"
```

### Task 1-2: CommentRepository 수정

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/repository/CommentRepository.java`

- [ ] **Step 1: 루트 댓글 조회, 대댓글 조회, replyCount 쿼리 추가**

```java
// 루트 댓글 조회 (deletedAt 상관없이 전부, parent가 null인 것만)
@Query("SELECT c FROM Comment c WHERE c.petPost = :petPost AND c.parent IS NULL ORDER BY c.createdAt DESC")
Page<Comment> findRootComments(@Param("petPost") PetPost petPost, Pageable pageable);

// 대댓글 조회 (삭제되지 않은 것만)
@Query("SELECT c FROM Comment c WHERE c.parent = :parent AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
Page<Comment> findReplies(@Param("parent") Comment parent, Pageable pageable);

// 대댓글 수 (삭제되지 않은 것만)
@Query("SELECT COUNT(c) FROM Comment c WHERE c.parent = :parent AND c.deletedAt IS NULL")
long countReplies(@Param("parent") Comment parent);
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/comment/repository/CommentRepository.java
git commit -m "feat: 루트 댓글, 대댓글, replyCount 쿼리 추가"
```

### Task 1-3: CommentCreateRequest 수정

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/dto/request/CommentCreateRequest.java`

- [ ] **Step 1: parentId 필드 추가**

```java
@Schema(description = "부모 댓글 ID (대댓글인 경우)", example = "1")
private Long parentId;
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/comment/dto/request/CommentCreateRequest.java
git commit -m "feat: CommentCreateRequest에 parentId 추가"
```

### Task 1-4: CommentResponse 수정

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/dto/response/CommentResponse.java`

- [ ] **Step 1: parentId, deleted, replyCount 필드 추가, from 메서드 수정**

```java
@Getter
@Builder
public class CommentResponse {

    private Long id;
    private String content;
    private Long parentId;
    private boolean deleted;
    private long replyCount;
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommentResponse from(Comment comment, long replyCount) {
        boolean isDeleted = comment.getDeletedAt() != null;
        boolean isUserDeleted = comment.getUser().isDeleted();

        String displayContent = isDeleted ? "삭제된 댓글입니다." : comment.getContent();
        String displayNickname = isUserDeleted ? "탈퇴한 사용자" : comment.getUser().getNickname();
        String displayProfileImage = isUserDeleted ? null : comment.getUser().getProfileImageUrl();

        return CommentResponse.builder()
                .id(comment.getId())
                .content(displayContent)
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .deleted(isDeleted)
                .replyCount(replyCount)
                .userId(comment.getUser().getId())
                .nickname(displayNickname)
                .profileImageUrl(displayProfileImage)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    // 대댓글용 (replyCount 없음)
    public static CommentResponse from(Comment comment) {
        return from(comment, 0);
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/comment/dto/response/CommentResponse.java
git commit -m "feat: CommentResponse에 parentId, deleted, replyCount 추가"
```

### Task 1-5: ErrorCode 추가

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/global/code/ErrorCode.java`

- [ ] **Step 1: INVALID_PARENT_COMMENT 에러 코드 추가**

```java
INVALID_PARENT_COMMENT(400, "INVALID_PARENT_COMMENT", "유효하지 않은 부모 댓글입니다."),
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/global/code/ErrorCode.java
git commit -m "feat: INVALID_PARENT_COMMENT 에러 코드 추가"
```

### Task 1-6: CommentService / CommentServiceImpl 수정

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/service/CommentService.java`
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/service/CommentServiceImpl.java`

- [ ] **Step 1: CommentService 인터페이스에 getReplies 추가**

```java
PageResponse<CommentResponse> getReplies(Long commentId, int page, int size);
```

- [ ] **Step 2: CommentServiceImpl 전체 수정**

createComment — parentId 검증 로직:
```java
@Override
@Transactional
public CommentResponse createComment(Long postId, CommentCreateRequest request, Long userId) {
    PetPost petPost = petPostRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    Comment parent = null;
    if (request.getParentId() != null) {
        parent = commentRepository.findById(request.getParentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARENT_COMMENT));
        // 대댓글의 대댓글이면 → 루트를 찾아서 parent로 설정
        if (parent.getParent() != null) {
            parent = parent.getParent();
        }
        // parent가 같은 게시글의 댓글인지 검증
        if (!parent.getPetPost().getId().equals(postId)) {
            throw new BusinessException(ErrorCode.INVALID_PARENT_COMMENT);
        }
    }

    Comment comment = Comment.builder()
            .petPost(petPost)
            .user(user)
            .content(request.getContent())
            .parent(parent)
            .build();

    Comment saved = commentRepository.save(comment);
    return CommentResponse.from(saved);
}
```

getComments — 루트 댓글만 조회 + replyCount:
```java
@Override
public PageResponse<CommentResponse> getComments(Long postId, int page, int size) {
    PetPost petPost = petPostRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

    Page<CommentResponse> commentPage = commentRepository
            .findRootComments(petPost, PageRequest.of(page, size))
            .map(comment -> {
                // 삭제된 루트 댓글 + 대댓글 없으면 필터링
                long replyCount = commentRepository.countReplies(comment);
                if (comment.getDeletedAt() != null && replyCount == 0) {
                    return null;
                }
                return CommentResponse.from(comment, replyCount);
            });

    // null 필터링 (삭제된 댓글 + 대댓글 없는 경우 제외)
    List<CommentResponse> filtered = commentPage.getContent().stream()
            .filter(Objects::nonNull)
            .toList();

    return PageResponse.from(new PageImpl<>(filtered, commentPage.getPageable(), commentPage.getTotalElements()));
}
```

getReplies — 대댓글 조회:
```java
@Override
public PageResponse<CommentResponse> getReplies(Long commentId, int page, int size) {
    Comment parent = commentRepository.findById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

    Page<CommentResponse> replyPage = commentRepository
            .findReplies(parent, PageRequest.of(page, size))
            .map(CommentResponse::from);
    return PageResponse.from(replyPage);
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/comment/service/CommentService.java
git add src/main/java/com/jipsamoye/backend/domain/comment/service/CommentServiceImpl.java
git commit -m "feat: 대댓글 생성/조회 서비스 로직 구현"
```

### Task 1-7: CommentController 수정

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/controller/CommentController.java`

- [ ] **Step 1: 대댓글 조회 엔드포인트 추가**

```java
@Operation(summary = "대댓글 목록 조회", description = "특정 댓글의 대댓글 목록을 조회합니다.")
@GetMapping("/api/comments/{commentId}/replies")
public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getReplies(
        @Parameter(description = "댓글 ID") @PathVariable Long commentId,
        @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size) {
    PageResponse<CommentResponse> response = commentService.getReplies(commentId, page, size);
    return ResponseEntity.ok(ApiResponse.success(response));
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/comment/controller/CommentController.java
git commit -m "feat: GET /api/comments/{commentId}/replies 엔드포인트 추가"
```

### Task 1-8: Phase 1 통합 테스트

- [ ] **Step 1: 빌드 및 서버 기동 테스트**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: develop에 push**

```bash
git checkout -b feature/reply-comments
git push -u origin feature/reply-comments
git checkout develop && git merge feature/reply-comments && git push origin develop
```

- [ ] **Step 3: develop → main PR 생성 및 머지**

- [ ] **Step 4: feature 브랜치 삭제, develop 최신 동기화**

---

## Phase 2: 실시간 알림

### Task 2-1: NotificationType Enum 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/notification/entity/NotificationType.java`

- [ ] **Step 1: Enum 작성**

```java
package com.jipsamoye.backend.domain.notification.entity;

public enum NotificationType {
    LIKE,
    COMMENT,
    REPLY,
    FOLLOW
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/notification/entity/NotificationType.java
git commit -m "feat: NotificationType enum 생성"
```

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

- [ ] **Step 2: Impl 작성**

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @Override
    @Transactional
    public void send(User receiver, User sender, NotificationType type, Long targetId, String message) {
        // 본인 행동에는 알림 안 보냄
        if (receiver.getId().equals(sender.getId())) return;

        Notification notification = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type(type)
                .targetId(targetId)
                .message(message)
                .build();

        notificationRepository.save(notification);

        // WebSocket으로 실시간 전송
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
git commit -m "feat: NotificationService 생성 (비동기 WebSocket 알림 전송)"
```

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

### Task 2-7: 기존 서비스에 알림 연동

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/like/service/LikeServiceImpl.java`
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/service/CommentServiceImpl.java`
- Modify: `src/main/java/com/jipsamoye/backend/domain/follow/service/FollowServiceImpl.java`

- [ ] **Step 1: LikeServiceImpl에 알림 추가**

NotificationService 주입 후 toggleLike에서 좋아요 추가 시:
```java
notificationService.send(
    petPost.getUser(), user,
    NotificationType.LIKE, postId,
    user.getNickname() + "님이 게시글에 좋아요를 눌렀습니다");
```

- [ ] **Step 2: CommentServiceImpl에 알림 추가**

createComment에서:
```java
// 루트 댓글 → 게시글 작성자에게 COMMENT 알림
if (parent == null) {
    notificationService.send(
        petPost.getUser(), user,
        NotificationType.COMMENT, postId,
        user.getNickname() + "님이 게시글에 댓글을 달았습니다");
}
// 대댓글 → 부모 댓글 작성자에게 REPLY 알림
else {
    notificationService.send(
        parent.getUser(), user,
        NotificationType.REPLY, parent.getId(),
        user.getNickname() + "님이 댓글에 답글을 달았습니다");
}
```

- [ ] **Step 3: FollowServiceImpl에 알림 추가**

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
git add src/main/java/com/jipsamoye/backend/domain/comment/service/CommentServiceImpl.java
git add src/main/java/com/jipsamoye/backend/domain/follow/service/FollowServiceImpl.java
git commit -m "feat: 좋아요/댓글/팔로우 시 알림 발송 연동"
```

### Task 2-8: Phase 2 배포

- [ ] **Step 1: feature → develop → main PR/머지**
- [ ] **Step 2: feature 브랜치 삭제, develop 최신 동기화**

---

## Phase 3-1: 오픈채팅

### Task 3-1-1: ChatMessage 엔티티 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/chat/entity/ChatMessage.java`

- [ ] **Step 1: 엔티티 작성**

```java
package com.jipsamoye.backend.domain.chat.entity;

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
@Table(name = "chat_messages")
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String anonymousNickname;

    @Builder
    public ChatMessage(User sender, String content, String anonymousNickname) {
        this.sender = sender;
        this.content = content;
        this.anonymousNickname = anonymousNickname;
    }
}
```

- [ ] **Step 2: ChatMessageRepository 생성**

```java
package com.jipsamoye.backend.domain.chat.repository;

import com.jipsamoye.backend.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/chat/
git commit -m "feat: ChatMessage 엔티티 및 Repository 생성"
```

### Task 3-1-2: ChatMessageResponse, ChatService 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/chat/dto/response/ChatMessageResponse.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/chat/service/ChatService.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/chat/service/ChatServiceImpl.java`

- [ ] **Step 1: Response DTO**

```java
package com.jipsamoye.backend.domain.chat.dto.response;

import com.jipsamoye.backend.domain.chat.entity.ChatMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponse {

    private Long id;
    private String content;
    private String anonymousNickname;
    private LocalDateTime createdAt;

    public static ChatMessageResponse from(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .anonymousNickname(message.getAnonymousNickname())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 2: Service 인터페이스 + Impl (랜덤 닉네임 포함)**

```java
package com.jipsamoye.backend.domain.chat.service;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import java.util.List;

public interface ChatService {
    List<ChatMessageResponse> getRecentMessages(int size);
    ChatMessageResponse sendMessage(Long userId, String content);
}
```

```java
package com.jipsamoye.backend.domain.chat.service;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.entity.ChatMessage;
import com.jipsamoye.backend.domain.chat.repository.ChatMessageRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    private final ConcurrentHashMap<Long, String> nicknameCache = new ConcurrentHashMap<>();
    private static final String[] NICKNAME_PREFIXES = {"멍집사", "냥집사", "댕댕이맘", "산책러", "간식요정"};
    private final Random random = new Random();

    @Override
    public List<ChatMessageResponse> getRecentMessages(int size) {
        List<ChatMessageResponse> messages = chatMessageRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        List<ChatMessageResponse> reversed = new java.util.ArrayList<>(messages);
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long userId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String nickname = nicknameCache.computeIfAbsent(userId,
                id -> "익명의 " + NICKNAME_PREFIXES[random.nextInt(NICKNAME_PREFIXES.length)] + random.nextInt(99));

        ChatMessage message = ChatMessage.builder()
                .sender(user)
                .content(content)
                .anonymousNickname(nickname)
                .build();

        chatMessageRepository.save(message);
        return ChatMessageResponse.from(message);
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/chat/
git commit -m "feat: ChatService + 랜덤 닉네임 생성 구현"
```

### Task 3-1-3: ChatController + WebSocket 컨트롤러 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/chat/controller/ChatController.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/chat/controller/ChatWebSocketController.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/chat/dto/request/ChatSendRequest.java`

- [ ] **Step 1: REST 컨트롤러**

```java
package com.jipsamoye.backend.domain.chat.controller;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.service.ChatService;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Chat", description = "오픈채팅 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "최근 메시지 조회")
    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getRecentMessages(
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getRecentMessages(size)));
    }
}
```

- [ ] **Step 2: WebSocket 컨트롤러 + Request DTO**

```java
package com.jipsamoye.backend.domain.chat.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatSendRequest {
    private Long userId;
    private String content;
}
```

```java
package com.jipsamoye.backend.domain.chat.controller;

import com.jipsamoye.backend.domain.chat.dto.request.ChatSendRequest;
import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/send")
    public void sendMessage(ChatSendRequest request) {
        ChatMessageResponse response = chatService.sendMessage(request.getUserId(), request.getContent());
        messagingTemplate.convertAndSend("/sub/chat/room", response);
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/chat/
git commit -m "feat: 오픈채팅 REST + WebSocket 컨트롤러 생성"
```

### Task 3-1-4: Phase 3-1 배포

- [ ] **Step 1: feature → develop → main PR/머지**
- [ ] **Step 2: feature 브랜치 삭제, develop 최신 동기화**

---

## Phase 3-2: DM

### Task 3-2-1: DmRoom, DmMessage 엔티티 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/entity/DmRoom.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/entity/DmMessage.java`

- [ ] **Step 1: DmRoom 엔티티**

```java
package com.jipsamoye.backend.domain.dm.entity;

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
@Table(name = "dm_rooms", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user1_id", "user2_id"})
})
public class DmRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @Builder
    public DmRoom(User user1, User user2) {
        this.user1 = user1;
        this.user2 = user2;
    }

    public boolean isParticipant(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }
}
```

- [ ] **Step 2: DmMessage 엔티티**

```java
package com.jipsamoye.backend.domain.dm.entity;

import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "dm_messages")
public class DmMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private DmRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String imageUrl;

    private LocalDateTime readAt;

    @Builder
    public DmMessage(DmRoom room, User sender, String content, String imageUrl) {
        this.room = room;
        this.sender = sender;
        this.content = content;
        this.imageUrl = imageUrl;
    }

    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/dm/entity/
git commit -m "feat: DmRoom, DmMessage 엔티티 생성"
```

### Task 3-2-2: DM Repository 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/repository/DmRoomRepository.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/repository/DmMessageRepository.java`

- [ ] **Step 1: Repository 작성**

```java
package com.jipsamoye.backend.domain.dm.repository;

import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

    @Query("SELECT r FROM DmRoom r WHERE (r.user1 = :u1 AND r.user2 = :u2) OR (r.user1 = :u2 AND r.user2 = :u1)")
    Optional<DmRoom> findByUsers(@Param("u1") User u1, @Param("u2") User u2);

    @Query("SELECT r FROM DmRoom r WHERE r.user1 = :user OR r.user2 = :user ORDER BY r.updatedAt DESC")
    List<DmRoom> findAllByUser(@Param("user") User user);
}
```

```java
package com.jipsamoye.backend.domain.dm.repository;

import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

    Page<DmMessage> findAllByRoomOrderByCreatedAtDesc(DmRoom room, Pageable pageable);

    // 채팅방의 가장 최근 메시지
    Optional<DmMessage> findFirstByRoomOrderByCreatedAtDesc(DmRoom room);

    // 안읽은 메시지 수 (상대방이 보낸 것 중 readAt이 null인 것)
    @Query("SELECT COUNT(m) FROM DmMessage m WHERE m.room = :room AND m.sender != :user AND m.readAt IS NULL")
    long countUnread(@Param("room") DmRoom room, @Param("user") User user);

    // 안읽은 메시지 일괄 읽음 처리
    @Modifying
    @Query("UPDATE DmMessage m SET m.readAt = CURRENT_TIMESTAMP WHERE m.room = :room AND m.sender != :user AND m.readAt IS NULL")
    void markAllAsRead(@Param("room") DmRoom room, @Param("user") User user);
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/dm/repository/
git commit -m "feat: DmRoomRepository, DmMessageRepository 생성"
```

### Task 3-2-3: DM DTO 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/dto/request/DmSendRequest.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/dto/response/DmRoomResponse.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/dto/response/DmMessageResponse.java`

- [ ] **Step 1: DTO 작성**

```java
package com.jipsamoye.backend.domain.dm.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DmSendRequest {
    private Long userId;
    private Long roomId;
    private String content;
    private String imageUrl;
}
```

```java
package com.jipsamoye.backend.domain.dm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DmRoomResponse {

    private Long roomId;
    private Long otherUserId;
    private String otherUserNickname;
    private String otherUserProfileImageUrl;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
}
```

```java
package com.jipsamoye.backend.domain.dm.dto.response;

import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DmMessageResponse {

    private Long id;
    private Long senderId;
    private String senderNickname;
    private String content;
    private String imageUrl;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static DmMessageResponse from(DmMessage message) {
        return DmMessageResponse.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .senderNickname(message.getSender().getNickname())
                .content(message.getContent())
                .imageUrl(message.getImageUrl())
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/dm/dto/
git commit -m "feat: DM DTO (Request, Response) 생성"
```

### Task 3-2-4: DmService 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/service/DmService.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/service/DmServiceImpl.java`

- [ ] **Step 1: 인터페이스**

```java
package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.global.response.PageResponse;

import java.util.List;

public interface DmService {
    List<DmRoomResponse> getRooms(Long userId);
    DmRoomResponse createRoom(Long userId, Long targetUserId);
    PageResponse<DmMessageResponse> getMessages(Long roomId, Long userId, int page, int size);
    DmMessageResponse sendMessage(Long userId, Long roomId, String content, String imageUrl);
}
```

- [ ] **Step 2: Impl**

```java
package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmServiceImpl implements DmService {

    private final DmRoomRepository dmRoomRepository;
    private final DmMessageRepository dmMessageRepository;
    private final UserRepository userRepository;

    @Override
    public List<DmRoomResponse> getRooms(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return dmRoomRepository.findAllByUser(user).stream()
                .map(room -> {
                    User other = room.getUser1().getId().equals(userId) ? room.getUser2() : room.getUser1();
                    var lastMsg = dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(room);
                    long unread = dmMessageRepository.countUnread(room, user);

                    return DmRoomResponse.builder()
                            .roomId(room.getId())
                            .otherUserId(other.getId())
                            .otherUserNickname(other.getNickname())
                            .otherUserProfileImageUrl(other.getProfileImageUrl())
                            .lastMessage(lastMsg.map(DmMessage::getContent).orElse(null))
                            .lastMessageAt(lastMsg.map(DmMessage::getCreatedAt).orElse(null))
                            .unreadCount(unread)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public DmRoomResponse createRoom(Long userId, Long targetUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "자기 자신에게 DM을 보낼 수 없습니다.");
        }

        DmRoom room = dmRoomRepository.findByUsers(user, target)
                .orElseGet(() -> dmRoomRepository.save(DmRoom.builder()
                        .user1(user)
                        .user2(target)
                        .build()));

        return DmRoomResponse.builder()
                .roomId(room.getId())
                .otherUserId(target.getId())
                .otherUserNickname(target.getNickname())
                .otherUserProfileImageUrl(target.getProfileImageUrl())
                .unreadCount(0)
                .build();
    }

    @Override
    @Transactional
    public PageResponse<DmMessageResponse> getMessages(Long roomId, Long userId, int page, int size) {
        DmRoom room = dmRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "채팅방을 찾을 수 없습니다."));

        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 채팅방 열 때 안읽은 메시지 일괄 읽음 처리
        dmMessageRepository.markAllAsRead(room, user);

        Page<DmMessageResponse> messagePage = dmMessageRepository
                .findAllByRoomOrderByCreatedAtDesc(room, PageRequest.of(page, size))
                .map(DmMessageResponse::from);
        return PageResponse.from(messagePage);
    }

    @Override
    @Transactional
    public DmMessageResponse sendMessage(Long userId, Long roomId, String content, String imageUrl) {
        DmRoom room = dmRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "채팅방을 찾을 수 없습니다."));

        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        DmMessage message = DmMessage.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .imageUrl(imageUrl)
                .build();

        dmMessageRepository.save(message);
        return DmMessageResponse.from(message);
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/dm/service/
git commit -m "feat: DmService 구현 (채팅방 생성, 메시지 조회/전송, 읽음 처리)"
```

### Task 3-2-5: DM Controller + WebSocket 컨트롤러 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/controller/DmController.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/dm/controller/DmWebSocketController.java`

- [ ] **Step 1: REST 컨트롤러**

```java
package com.jipsamoye.backend.domain.dm.controller;

import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.service.DmService;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "DM", description = "DM API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dm")
public class DmController {

    private final DmService dmService;

    @Operation(summary = "채팅방 목록 조회")
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<DmRoomResponse>>> getRooms(@RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success(dmService.getRooms(userId)));
    }

    @Operation(summary = "채팅방 생성")
    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<DmRoomResponse>> createRoom(
            @RequestParam Long userId, @RequestParam Long targetUserId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(dmService.createRoom(userId, targetUserId)));
    }

    @Operation(summary = "메시지 목록 조회")
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<DmMessageResponse>>> getMessages(
            @PathVariable Long roomId, @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(dmService.getMessages(roomId, userId, page, size)));
    }
}
```

- [ ] **Step 2: WebSocket 컨트롤러**

```java
package com.jipsamoye.backend.domain.dm.controller;

import com.jipsamoye.backend.domain.dm.dto.request.DmSendRequest;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.service.DmService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DmWebSocketController {

    private final DmService dmService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/dm/send")
    public void sendMessage(DmSendRequest request) {
        DmMessageResponse response = dmService.sendMessage(
                request.getUserId(), request.getRoomId(),
                request.getContent(), request.getImageUrl());

        messagingTemplate.convertAndSend("/sub/dm/room/" + request.getRoomId(), response);
    }
}
```

- [ ] **Step 3: ImageServiceImpl에 dm dirName 추가**

```java
if (!dirName.equals("posts") && !dirName.equals("profiles") && !dirName.equals("covers") && !dirName.equals("dm")) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, "dirName은 posts, profiles, covers, dm만 가능합니다.");
}
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/dm/controller/
git add src/main/java/com/jipsamoye/backend/domain/image/service/ImageServiceImpl.java
git commit -m "feat: DM REST + WebSocket 컨트롤러 생성, dm dirName 추가"
```

### Task 3-2-6: Phase 3-2 배포

- [ ] **Step 1: feature → develop → main PR/머지**
- [ ] **Step 2: feature 브랜치 삭제, develop 최신 동기화**

---

## 전체 진행 체크리스트

```
Phase 0 — 공통 설정
  [ ] Task 0-1: OSIV 비활성화
  [ ] Task 0-2: WebSocket 의존성 추가
  [ ] Task 0-3: WebSocketConfig 생성
  [ ] Task 0-4: AsyncConfig 생성

Phase 1 — 대댓글
  [ ] Task 1-1: Comment 엔티티 수정
  [ ] Task 1-2: CommentRepository 수정
  [ ] Task 1-3: CommentCreateRequest 수정
  [ ] Task 1-4: CommentResponse 수정
  [ ] Task 1-5: ErrorCode 추가
  [ ] Task 1-6: CommentService/Impl 수정
  [ ] Task 1-7: CommentController 수정
  [ ] Task 1-8: Phase 1 배포

Phase 2 — 실시간 알림
  [ ] Task 2-1: NotificationType Enum
  [ ] Task 2-2: Notification 엔티티
  [ ] Task 2-3: NotificationRepository
  [ ] Task 2-4: NotificationResponse
  [ ] Task 2-5: NotificationService
  [ ] Task 2-6: NotificationController
  [ ] Task 2-7: 기존 서비스 알림 연동
  [ ] Task 2-8: Phase 2 배포

Phase 3-1 — 오픈채팅
  [ ] Task 3-1-1: ChatMessage 엔티티 + Repository
  [ ] Task 3-1-2: ChatService + 랜덤 닉네임
  [ ] Task 3-1-3: ChatController + WebSocket
  [ ] Task 3-1-4: Phase 3-1 배포

Phase 3-2 — DM
  [ ] Task 3-2-1: DmRoom, DmMessage 엔티티
  [ ] Task 3-2-2: DM Repository
  [ ] Task 3-2-3: DM DTO
  [ ] Task 3-2-4: DmService
  [ ] Task 3-2-5: DM Controller + WebSocket + dm dirName
  [ ] Task 3-2-6: Phase 3-2 배포
```
