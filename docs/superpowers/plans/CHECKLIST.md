# 소셜 기능 고도화 — 전체 진행 체크리스트

> Phase 완료 시 체크 표시. 각 Phase 상세는 개별 계획서 참조.

---

## Phase 0 — 공통 설정
> 📄 `2026-04-14-phase0-common.md`

- [ ] Task 0-1: OSIV 비활성화
- [ ] Task 0-2: WebSocket 의존성 추가
- [ ] Task 0-3: WebSocketConfig 생성
- [ ] Task 0-4: Phase 0 배포

---

## Phase 1 — 대댓글
> 📄 `2026-04-14-phase1-reply.md`

- [ ] Task 1-1: Comment 엔티티 수정 (parent 추가, @SQLRestriction 제거)
- [ ] Task 1-2: CommentRepository 수정 (루트/대댓글/replyCount 쿼리)
- [ ] Task 1-3: CommentCreateRequest에 parentId 추가
- [ ] Task 1-4: CommentResponse에 parentId, deleted, replyCount 추가
- [ ] Task 1-5: ErrorCode INVALID_PARENT_COMMENT 추가
- [ ] Task 1-6: CommentService/Impl 수정 (대댓글 생성/조회)
- [ ] Task 1-7: CommentController 대댓글 엔드포인트 추가
- [ ] Task 1-8: Phase 1 배포

---

## Phase 2 — 실시간 알림
> 📄 `2026-04-14-phase2-notification.md`

- [ ] Task 2-1: NotificationType Enum 생성
- [ ] Task 2-2: Notification 엔티티 생성
- [ ] Task 2-3: NotificationRepository 생성
- [ ] Task 2-4: NotificationResponse DTO 생성
- [ ] Task 2-5: NotificationService 생성 (WebSocket)
- [ ] Task 2-6: NotificationController 생성
- [ ] Task 2-7: 기존 서비스 알림 연동 (Like, Comment, Follow)
- [ ] Task 2-8: Phase 2 배포

---

## Phase 3-1 — 오픈채팅
> 📄 `2026-04-14-phase3-1-chat.md`

- [ ] Task 3-1-1: ChatMessage 엔티티 + Repository 생성
- [ ] Task 3-1-2: ChatService + 랜덤 닉네임 구현
- [ ] Task 3-1-3: ChatController + WebSocket 컨트롤러 생성
- [ ] Task 3-1-4: Phase 3-1 배포

---

## Phase 3-2 — DM
> 📄 `2026-04-14-phase3-2-dm.md`

- [ ] Task 3-2-1: DmRoom, DmMessage 엔티티 생성
- [ ] Task 3-2-2: DM Repository 생성
- [ ] Task 3-2-3: DM DTO 생성
- [ ] Task 3-2-4: DmService 생성
- [ ] Task 3-2-5: DM Controller + WebSocket + dm dirName
- [ ] Task 3-2-6: Phase 3-2 배포
