# 집사모여 — 소셜 기능 고도화 백엔드 설계

> **작성일:** 2026-04-14
> **목표:** 프론트엔드 소셜 기능 고도화 계획에 맞는 백엔드 API 설계
> **범위:** 대댓글, 실시간 알림, 오픈채팅, DM

---

## 공통 변경

### OSIV 비활성화
```yaml
spring.jpa.open-in-view: false
```
실시간 연결(WebSocket)에서 DB 커넥션 점유 방지. 운영 환경 권장사항.

### WebSocket 통합
알림, 오픈채팅, DM 모두 **하나의 WebSocket 연결(`/ws`)** 로 통합.
- 프로토콜: STOMP
- 브로커: Spring 내장 Simple Broker
- 유저 1명당 연결 1개, 구독으로 채널 구분

```
/sub/notifications/{userId}  ← 알림
/sub/chat/room               ← 오픈채팅
/sub/dm/room/{roomId}        ← DM
```

### 인증
현재 임시 `?userId=` 파라미터 방식 유지. 추후 OAuth 구현 시 교체.

---

## Phase 1-2: 대댓글

### 방식
유튜브 방식 (depth 1 + @멘션). parent는 항상 루트 댓글만 가리킴.

### 엔티티 변경: Comment
- `parent` (ManyToOne, self-reference) 추가 — null이면 루트, 있으면 대댓글
- `@SQLRestriction` 제거 — 서비스에서 삭제 여부 처리

### API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/posts/{postId}/comments?page=0&size=20` | 루트 댓글 조회 (replyCount 포함) |
| GET | `/api/comments/{commentId}/replies?page=0&size=10` | 대댓글 조회 |
| POST | `/api/posts/{postId}/comments?userId=` | 댓글/대댓글 작성 (body에 parentId) |
| PATCH | `/api/comments/{id}?userId=` | 수정 |
| DELETE | `/api/comments/{id}?userId=` | 삭제 |

### 요청 예시 (대댓글 작성)
```json
{
  "content": "@멍집사 맞아요!",
  "parentId": 1
}
```

### 응답 예시 (루트 댓글)
```json
{
  "id": 1,
  "content": "귀여워요!",
  "parentId": null,
  "deleted": false,
  "replyCount": 3,
  "userId": 1,
  "nickname": "멍집사",
  "profileImageUrl": "...",
  "createdAt": "..."
}
```

### 삭제 처리
- 대댓글 있는 루트 댓글 삭제 → "삭제된 댓글입니다" 표시, 대댓글 유지
- 대댓글 없는 루트 댓글 삭제 → 안 보여줌
- 대댓글 삭제 → 안 보여줌

### 서비스 로직
- 대댓글 작성 시 parentId가 루트 댓글인지 검증
- parentId가 대댓글이면 → 그 대댓글의 루트를 찾아서 parent로 설정
- replyCount는 DB 컬럼 없이 count 쿼리로 조회

---

## Phase 2: 실시간 알림

### 새 도메인: `domain/notification/`

### 엔티티: Notification

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| receiver | User (ManyToOne) | 알림 받는 사람 |
| sender | User (ManyToOne) | 알림 발생시킨 사람 |
| type | NotificationType (Enum) | LIKE, COMMENT, REPLY, FOLLOW |
| targetId | Long | 관련 게시글/댓글 ID |
| message | String | 알림 메시지 |
| isRead | boolean | 읽음 여부 |
| createdAt | LocalDateTime | (BaseEntity) |

### API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/notifications?userId=&page=0&size=20` | 알림 목록 조회 |
| PATCH | `/api/notifications/{id}/read?userId=` | 읽음 처리 |
| GET | `/api/notifications/unread-count?userId=` | 미읽은 수 |

### 실시간 전송
- WebSocket STOMP `/sub/notifications/{userId}` 구독
- 알림 발생 시 `SimpMessagingTemplate`으로 전송

### 알림 발생 시점

| 이벤트 | 발생 위치 | 알림 메시지 |
|--------|----------|-----------|
| 좋아요 | LikeServiceImpl | "{닉네임}님이 게시글에 좋아요를 눌렀습니다" |
| 댓글 | CommentServiceImpl | "{닉네임}님이 게시글에 댓글을 달았습니다" |
| 대댓글 | CommentServiceImpl | "{닉네임}님이 댓글에 답글을 달았습니다" |
| 팔로우 | FollowServiceImpl | "{닉네임}님이 회원님을 팔로우했습니다" |

- 본인 행동에는 알림 안 보냄
- 알림 생성은 `@Async`로 비동기 처리 (API 응답 지연 방지)

---

## Phase 3-1: 오픈채팅

### 새 도메인: `domain/chat/`

### 엔티티: ChatMessage

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| sender | User (ManyToOne) | 보낸 사람 |
| content | String (TEXT) | 메시지 내용 |
| anonymousNickname | String | 랜덤 닉네임 |
| createdAt | LocalDateTime | (BaseEntity) |

### API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/chat/messages?size=50` | 최근 메시지 조회 |

### WebSocket (STOMP)

| 방향 | Destination | 설명 |
|------|-------------|------|
| 발행 | `/pub/chat/send` | 메시지 전송 |
| 구독 | `/sub/chat/room` | 메시지 수신 |

### 랜덤 닉네임
- 접속 시 서버에서 생성: `"익명의"` + `["멍집사", "냥집사", "댕댕이맘", "산책러", "간식요정"]` + 랜덤 숫자(1~99)
- 유저-닉네임 매핑을 메모리(ConcurrentHashMap)에 캐싱
- 같은 유저가 재접속해도 동일 닉네임 유지 (서버 재시작 시 초기화)

---

## Phase 3-2: DM

### 새 도메인: `domain/dm/`

### 엔티티: DmRoom

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| user1 | User (ManyToOne) | 참여자 1 |
| user2 | User (ManyToOne) | 참여자 2 |
| createdAt | LocalDateTime | (BaseEntity) |

### 엔티티: DmMessage

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| room | DmRoom (ManyToOne) | 채팅방 |
| sender | User (ManyToOne) | 보낸 사람 |
| content | String (TEXT) | 메시지 내용 |
| imageUrl | String | 이미지 URL (선택, 기존 Presigned URL 재사용) |
| readAt | LocalDateTime | 읽은 시점 (null이면 안 읽음) |
| createdAt | LocalDateTime | (BaseEntity) |

### API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/dm/rooms?userId=` | 채팅방 목록 (최근 메시지, 안읽은 수 포함) |
| POST | `/api/dm/rooms?userId=&targetUserId=` | 채팅방 생성 (이미 있으면 기존 반환) |
| GET | `/api/dm/rooms/{roomId}/messages?page=0&size=50` | 메시지 목록 |

### WebSocket (STOMP)

| 방향 | Destination | 설명 |
|------|-------------|------|
| 발행 | `/pub/dm/send` | 메시지 전송 |
| 구독 | `/sub/dm/room/{roomId}` | 특정 채팅방 메시지 수신 |

### 읽음 처리
- 상대방이 해당 채팅방을 구독 중이면 즉시 readAt 설정
- 구독 안 중이면 채팅방 열 때 readAt 일괄 업데이트

### 이미지 전송
- 기존 `/api/images/presigned-url`에 `dirName: "dm"` 추가
- 프론트에서 S3 업로드 후 imageUrl을 메시지에 포함

---

## 새 패키지 구조

```
domain/
├── notification/
│   ├── controller/NotificationController.java
│   ├── service/NotificationService.java, NotificationServiceImpl.java
│   ├── entity/Notification.java, NotificationType.java
│   ├── repository/NotificationRepository.java
│   └── dto/response/NotificationResponse.java
├── chat/
│   ├── controller/ChatController.java, ChatWebSocketController.java
│   ├── service/ChatService.java, ChatServiceImpl.java
│   ├── entity/ChatMessage.java
│   ├── repository/ChatMessageRepository.java
│   └── dto/response/ChatMessageResponse.java
├── dm/
│   ├── controller/DmController.java, DmWebSocketController.java
│   ├── service/DmService.java, DmServiceImpl.java
│   ├── entity/DmRoom.java, DmMessage.java
│   ├── repository/DmRoomRepository.java, DmMessageRepository.java
│   └── dto/request/, dto/response/
```

## 설정 추가

```
global/config/
├── WebSocketConfig.java  ← STOMP 설정 (알림 + 채팅 + DM 공유)
├── AsyncConfig.java      ← @Async 설정 (알림 비동기 발송)
```

---

## 구현 순서

각 Phase를 독립적으로 설계 → 구현 → 배포:

1. **대댓글** — Comment 엔티티 확장, API 2개 추가
2. **WebSocket 인프라** — WebSocketConfig, STOMP 설정
3. **실시간 알림** — Notification 도메인 + WebSocket 전송
4. **오픈채팅** — ChatMessage 도메인 + STOMP 송수신
5. **DM** — DmRoom/DmMessage 도메인 + STOMP 송수신 + 읽음 처리
