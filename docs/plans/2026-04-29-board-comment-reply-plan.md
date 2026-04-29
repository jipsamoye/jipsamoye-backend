# Board(자유게시판) 대댓글 도입 Implementation Plan (PR 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`).

**Goal:** 자유게시판(Board)에 1-depth 답글 도입 — PR 1(PetPost) 패턴 그대로 복제 + 알림 `relatedPostId=boardId` 전달.

**Architecture:** `BoardComment`에 `parent`, `mentionedUser`, `isMasked` 추가. 부모 댓글 페이지 + 각 부모의 답글 처음 3개 동봉. 부모 삭제 시 답글 있으면 마스킹·없으면 일반 soft delete. Board의 `commentCount` 컬럼은 이미 존재 — 별도 추가 불필요.

**Tech Stack:** Spring Boot 3.5.13, Java 17, MySQL 8.0(윈도우 함수), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/specs/2026-04-28-comment-reply-design.md` 참조. PR 1 plan(`docs/plans/2026-04-28-pet-post-comment-reply-plan.md`)과 동일 패턴.

---

## PR 1과의 차이점 (요약)

| 항목 | PR 1 (PetPost) | PR 2 (Board) |
|---|---|---|
| 카운트 컬럼 | 신규 추가 (`pet_post.comment_count`) | **이미 존재** — 컬럼 추가 불필요 |
| 카운트 메서드 | `incrementCommentCount`/`decrementCommentCount` 분리 | PR 1과 일관성 위해 분리 추가 (기존 `updateCommentCount(id, value)`는 별개) |
| 알림 type | `PET_POST_COMMENT_REPLY` | `BOARD_COMMENT_REPLY` 추가 |
| 알림 `relatedPostId` | `petPost.getId()` | `board.getId()` (PR 1에서 만든 인프라 활용) |
| 응답 DTO commentCount 노출 | 신규 (`PetPostResponse`/`PetPostListResponse`에 추가) | **이미 노출됨** — 별도 작업 불필요 |
| 컨트롤러 경로 | `/api/comments/...` (boardId 신규 path 변경) | `/api/board-comments/...` (기존 `/api/boards/{boardId}/comments` 패턴 → 통일) **breaking change** |
| 엔티티 메서드 명명 | `updateContent(content)` | 기존 `update(content)` → PR 1 일관성 위해 `updateContent(content)`로 변경 |

---

## File Structure

### 신규 파일
- `src/main/resources/db/migration/2026-04-29-board-comment-reply.sql` — 운영 DB ALTER 스크립트
- `src/test/java/.../boardComment/entity/BoardCommentTest.java` — 비즈니스 메서드 단위 테스트
- `src/test/java/.../boardComment/service/BoardCommentServiceImplTest.java` — Mockito 서비스 테스트

### 수정 파일

| 파일 | 변경 요약 |
|---|---|
| `notification/entity/NotificationType.java` | `BOARD_COMMENT_REPLY` 추가 |
| `board/repository/BoardRepository.java` | `incrementCommentCount`, `decrementCommentCount` (음수 가드) |
| `boardComment/entity/BoardComment.java` | `parent`, `mentionedUser`, `isMasked` + `mask()`, `isReply()`, `updateContent()` |
| `boardComment/repository/BoardCommentRepository.java` | 답글 쿼리·row lock·count |
| `boardComment/dto/request/BoardCommentCreateRequest.java` | `boardId`, `parentId`, `mentionedUserId`, `@Size(max=500)` |
| `boardComment/dto/request/BoardCommentUpdateRequest.java` | (변경 없음, 기존 그대로) |
| `boardComment/dto/response/BoardCommentResponse.java` | `mentionedNickname`, `isMasked`, `replyCount`, `replies`, `updatedAt` 추가 |
| `boardComment/service/BoardCommentService.java` | 새 메서드 시그니처 |
| `boardComment/service/BoardCommentServiceImpl.java` | 답글 흐름 (작성/삭제/조회) |
| `boardComment/controller/BoardCommentController.java` | 부모+답글 조회·답글 더보기 엔드포인트 (PR 1 패턴) |

---

## Task 1: DB 마이그레이션 SQL 작성

**Files:** Create `src/main/resources/db/migration/2026-04-29-board-comment-reply.sql`

내용: `board_comments` 테이블에 ① `parent_id`(FK→board_comments.id), `mentioned_user_id`(FK→users.id), `is_masked BOOLEAN NOT NULL DEFAULT FALSE` 추가. ② 인덱스 2개 — `idx_board_comments_board_parent_created (board_id, parent_id, created_at)`, `idx_board_comments_parent_created (parent_id, created_at)`.

> Board의 `comment_count` 컬럼은 이미 존재 — 백필 SQL 불필요.

- [ ] SQL 파일 작성
- [ ] 커밋 — `docs: Board 대댓글 도입 DB 마이그레이션 SQL 추가`

---

## Task 2: NotificationType.BOARD_COMMENT_REPLY 추가

**Files:** Modify `notification/entity/NotificationType.java`

기존 enum에 `BOARD_COMMENT_REPLY` 한 줄 추가.

- [ ] enum 값 추가
- [ ] `./gradlew compileJava` 통과 확인
- [ ] 커밋 — `feat: NotificationType에 BOARD_COMMENT_REPLY 추가`

---

## Task 3: BoardRepository increment/decrement 메서드 추가

**Files:** Modify `board/repository/BoardRepository.java`

PR 1과 일관성 위해 `incrementCommentCount`/`decrementCommentCount` 추가:
- `void incrementCommentCount(Long id)` — `@Modifying` JPQL UPDATE
- `void decrementCommentCount(Long id)` — `WHERE id = :id AND commentCount > 0` (음수 방지)

기존 `updateCommentCount(Long id, int value)`는 다른 호출처가 있을 수 있으니 그대로 둠.

- [ ] Repository 메서드 추가
- [ ] `./gradlew compileJava` 통과
- [ ] 커밋 — `feat: BoardRepository에 increment/decrement commentCount 메서드 추가`

---

## Task 4: BoardComment 엔티티 확장 + 비즈니스 메서드 + 단위 테스트 (TDD)

**Files:** Modify `boardComment/entity/BoardComment.java`, Create `src/test/java/.../boardComment/entity/BoardCommentTest.java`

엔티티 변경:
- `@ManyToOne(fetch=LAZY) @JoinColumn(name="parent_id") private BoardComment parent`
- `@ManyToOne(fetch=LAZY) @JoinColumn(name="mentioned_user_id") private User mentionedUser`
- `@Column(nullable=false) private boolean isMasked = false`
- `@Table(indexes = { ... })` 인덱스 2개 (DDL은 마이그레이션 SQL이 진실 원천, 매핑 일관성용)
- `@Builder` 시그니처에 `parent`, `mentionedUser` 추가
- 비즈니스 메서드: `void mask()`, `boolean isReply()`
- 기존 `update(content)` → `updateContent(content)`로 rename (PR 1 일관성)
  - 이 변경으로 기존 호출처(BoardCommentServiceImpl) 갱신 필요

단위 테스트 (BoardCommentTest):
- `isReply_distinguishesParentAndChild`
- `mask_setsFlagWithoutAlteringContent`
- `isMasked_defaultsFalse`

- [ ] 단위 테스트 작성 (failing)
- [ ] `./gradlew test --tests "*BoardCommentTest"` → FAIL 확인
- [ ] 엔티티 구현
- [ ] 테스트 PASS 확인
- [ ] 커밋 — `feat: BoardComment 엔티티에 parent·mentionedUser·isMasked 추가`

---

## Task 5: BoardCommentRepository 답글 쿼리 추가

**Files:** Modify `boardComment/repository/BoardCommentRepository.java`

추가할 메서드 시그니처:
- `Page<BoardComment> findParentsByBoardId(Long boardId, Pageable)` — JPQL `WHERE board.id = :boardId AND parent IS NULL ORDER BY createdAt DESC`
- `List<Object[]> countRepliesGroupedByParentIds(List<Long> parentIds)` — `SELECT parent.id, COUNT(c) GROUP BY parent.id`
- `List<BoardComment> findTop3RepliesByParentIds(List<Long> parentIds)` — **native query (MySQL 8.0 윈도우 함수)**
- `Page<BoardComment> findRepliesByParentId(Long parentId, Pageable)` — `ORDER BY createdAt ASC`
- `long countByParentAndDeletedAtIsNull(BoardComment parent)`
- `Optional<BoardComment> findByIdForUpdate(Long id)` — `@Lock(LockModeType.PESSIMISTIC_WRITE)`

윈도우 함수 native query:
```sql
SELECT * FROM (
  SELECT c.*, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY created_at ASC) AS rn
  FROM board_comments c
  WHERE c.parent_id IN (:parentIds) AND c.deleted_at IS NULL
) t WHERE t.rn <= 3
```
> `@SQLRestriction`은 native query에 적용 안 되므로 `deleted_at IS NULL` 명시 필터 필수.

- [ ] Repository 메서드 추가
- [ ] `./gradlew compileJava` 통과
- [ ] 커밋 — `feat: BoardCommentRepository에 답글 조회·row lock·count 메서드 추가`

---

## Task 6: BoardComment DTO 갱신 (요청/응답)

**Files:** Modify `boardComment/dto/request/BoardCommentCreateRequest.java`, `boardComment/dto/response/BoardCommentResponse.java`

- `BoardCommentCreateRequest`: `(Long boardId, Long parentId, Long mentionedUserId, String content)` — `@NotNull`, `@NotBlank @Size(max=500)`
  - boardId가 path → body로 이동 (PR 1 패턴 통일, breaking change)
- `BoardCommentUpdateRequest`: 변경 없음 (이미 `@Size(max=500)`)
- `BoardCommentResponse`: 기존 필드 + `mentionedNickname`(nullable), `isMasked`, `replyCount`, `replies(List<BoardCommentResponse>)`, `updatedAt`. 정적 팩토리 두 개:
  - `from(BoardComment, long replyCount, List<BoardCommentResponse> replies)` — 부모용. 마스킹 시 `content=null`
  - `ofReply(BoardComment)` — 답글용 (replyCount=0, replies=List.of())

> Service 변경(Task 7~9)에서 기존 `from(BoardComment)` 호출처를 모두 갱신하므로 일시적으로 컴파일 깨짐.

- [ ] DTO 2개 갱신
- [ ] 컴파일 깨짐 확인 (Task 7에서 정리)
- [ ] **이 단계는 별도 커밋 X** — Task 7 커밋에 묶음

---

## Task 7: BoardCommentService.create — 답글 작성 (TDD)

**Files:** Modify `boardComment/service/BoardCommentService.java`, `boardComment/service/BoardCommentServiceImpl.java`, Create `src/test/java/.../boardComment/service/BoardCommentServiceImplTest.java`

### 인터페이스 시그니처 갱신
```java
BoardCommentResponse create(BoardCommentCreateRequest request, Long userId);
BoardCommentResponse update(Long commentId, BoardCommentUpdateRequest request, Long userId);
void delete(Long commentId, Long userId);
PageResponse<BoardCommentResponse> getCommentsByBoard(Long boardId, Pageable pageable);
PageResponse<BoardCommentResponse> getReplies(Long parentId, Pageable pageable);
```
update/delete/getCommentsByBoard/getReplies는 이번 task에선 `throw new UnsupportedOperationException()` 두고 Task 8~10에서 구현.

### create 로직
1. user, board 검증
2. `parentId != null`이면:
   - parent fetch + 같은 게시글 검증 + 마스킹 검증 (마스킹이면 `BusinessException(BAD_REQUEST, "삭제된 댓글에는 답글을 달 수 없습니다.")`)
   - parent가 답글이면 (`parent.isReply()`) → **부모를 root로 자동 매핑** + `mentionedUser = parent.getUser()`
   - parent가 부모면 → 그대로 사용 + `mentionedUserId`가 있으면 fetch해서 mentionedUser 세팅
3. BoardComment 저장 + `boardRepository.incrementCommentCount(boardId)`
4. 알림: `notifyTarget = mentionedUser ?? parent.getUser()`. `notifyTarget.id != userId`이면 `eventPublisher.publishEvent(new NotificationEvent(notifyTarget, user, BOARD_COMMENT_REPLY, savedCommentId, board.getId(), message))`
   - **`relatedPostId = board.getId()`** (PR 1에서 만든 알림 인프라 활용)

### 단위 테스트 (BoardCommentServiceImplTest)
Mockito + `ReflectionTestUtils.setField(entity, "id", N)`으로 ID 주입.
- `create_reply_publishesNotificationEvent` — type, receiver, sender, **relatedPostId=boardId** 검증
- `create_selfReply_doesNotPublishEvent`
- `create_replyToReply_remapsRootAndPreservesMention` — saved.parent == root, saved.mentionedUser == 원답글작성자, **알림 receiver = 원답글작성자(B)** (PR 1과 동일 정책)
- `create_replyToMaskedParent_throwsException`

### 의존성
`@RequiredArgsConstructor`로 주입: `BoardCommentRepository`, `BoardRepository`, `UserRepository`, `ApplicationEventPublisher`. Service는 `@Transactional(readOnly=true)` 클래스 + `create`/`delete`/`update`에 `@Transactional` 오버라이드.

- [ ] 인터페이스 갱신
- [ ] 테스트 4건 작성 (failing)
- [ ] `./gradlew test --tests "*BoardCommentServiceImplTest"` → FAIL
- [ ] create 구현 (다른 메서드는 UnsupportedOperationException)
- [ ] 테스트 PASS
- [ ] 커밋 — `feat: BoardCommentServiceImpl 답글 작성 로직 (1-depth 강제, 마스킹 거부, 알림+relatedPostId)`

---

## Task 8: BoardCommentService.delete — 마스킹·cascade (TDD)

**Files:** Modify `boardComment/service/BoardCommentServiceImpl.java`, Modify `BoardCommentServiceImplTest.java`

### delete 로직 (PR 1 동일)
1. `boardCommentRepository.findByIdForUpdate(commentId)` — row lock
2. 작성자 검증 (다르면 `FORBIDDEN`)
3. 분기:
   - `comment.isReply()` → `comment.softDelete()` + `decrementCommentCount`. 부모가 마스킹이고 `countByParentAndDeletedAtIsNull(parent) == 0`이면 부모도 `softDelete()` + `decrementCommentCount` 추가 (cascade)
   - 부모 댓글이고 답글 있음 → `comment.mask()` (카운트 변동 없음)
   - 부모 댓글이고 답글 없음 → `comment.softDelete()` + `decrementCommentCount`

### 추가 단위 테스트
- `delete_parentWithoutReplies_softDeletesAndDecrements`
- `delete_parentWithReplies_masksAndKeepsCount`
- `delete_lastReplyOfMaskedParent_cascadesParentDelete` — `verify(boardRepository, times(2)).decrementCommentCount(...)`
- `delete_otherUsersComment_throwsForbidden`

- [ ] 테스트 4건 추가 (failing)
- [ ] FAIL 확인
- [ ] delete 구현
- [ ] 테스트 PASS (총 8건)
- [ ] 커밋 — `feat: BoardCommentServiceImpl 삭제 로직 (마스킹·cascade·row lock)`

---

## Task 9: BoardCommentService.getCommentsByBoard / getReplies — N+1 회피 조회 (TDD)

**Files:** Modify `boardComment/service/BoardCommentServiceImpl.java`, Modify `BoardCommentServiceImplTest.java`

### getCommentsByBoard 로직 (3-쿼리 N+1 회피, PR 1 동일)
1. `boardCommentRepository.findParentsByBoardId(boardId, pageable)`
2. parentIds 추출. 비어있으면 빈 페이지 매핑 후 즉시 반환
3. `countRepliesGroupedByParentIds(parentIds)` → `Map<Long, Long>`
4. `findTop3RepliesByParentIds(parentIds)` → `Map<Long, List<BoardComment>>` (Java `Collectors.groupingBy(r -> r.getParent().getId())`)
5. `parents.map(p -> BoardCommentResponse.from(p, countMap.getOrDefault(...0L), repliesMap.getOrDefault(...List.of()).map(ofReply)))`
6. `PageResponse.from(...)` 래핑

### getReplies 로직
- `boardCommentRepository.findRepliesByParentId(parentId, pageable).map(BoardCommentResponse::ofReply)` → `PageResponse.from`

### 단위 테스트
- `getCommentsByBoard_attachesRepliesAndCount`
- `getReplies_returnsOldestFirstPage`

- [ ] 테스트 2건 추가 (failing)
- [ ] FAIL 확인
- [ ] 두 메서드 구현
- [ ] 테스트 PASS (총 10건)
- [ ] 커밋 — `feat: BoardCommentServiceImpl 부모+답글 동봉 조회 + 답글 더보기 (N+1 회피)`

---

## Task 10: BoardCommentService.update + Controller 엔드포인트

**Files:** Modify `boardComment/service/BoardCommentServiceImpl.java`, `boardComment/controller/BoardCommentController.java`

### update 로직 (PR 1 동일)
- `findById` (lock 불필요) → 작성자 검증 → 마스킹 검증 (마스킹이면 `BAD_REQUEST`) → `comment.updateContent(...)`

### Controller 엔드포인트 (전체 재작성, PR 1 패턴)
- `POST /api/board-comments` → `create` (`@AuthenticationPrincipal CustomUserDetails`)
- `PATCH /api/board-comments/{commentId}` → `update`
- `DELETE /api/board-comments/{commentId}` → `delete`
- `GET /api/board-comments/board/{boardId}?page=&size=` → `getCommentsByBoard` (size `@Min(1) @Max(50)`)
- `GET /api/board-comments/{parentId}/replies?page=&size=` → `getReplies` (size `@Min(1) @Max(50)`)

`@RestController @RequestMapping("/api/board-comments") @Validated` + Swagger `@Tag/@Operation`. 응답은 `ResponseEntity<ApiResponse<...>>`. POST는 201 Created (`ApiResponse.created`), 나머진 200.

> **Breaking change**: 기존 `POST /api/boards/{boardId}/comments`, `GET /api/boards/{boardId}/comments`, `PATCH /api/board-comments/{id}`, `DELETE /api/board-comments/{id}` 모두 신규 경로로 교체. 프론트 동시 작업 필요.

- [ ] update 구현
- [ ] Controller 전체 갱신
- [ ] `./gradlew build` 통과 확인
- [ ] 커밋 — `feat: BoardCommentController 답글 엔드포인트 + update 로직`

---

## Task 11: 빌드 검증 + ArchUnit + 머지

**Files:** (변경 없음)

- [ ] `./gradlew build` 전체 통과
- [ ] `./gradlew test --tests "*ArchitectureTest"` — ArchUnit 컨벤션 검증
- [ ] develop으로 push
- [ ] **사용자 확인 후** PR #54 생성 (base: develop)
- [ ] PR 본문에 다음 명시:
  - 백/프론트 동시 배포 필요 (응답 구조 변경, 컨트롤러 경로 변경)
  - 운영 DB 수동 마이그레이션 SQL 1회 실행 필요 (`2026-04-29-board-comment-reply.sql`)
  - PR 1과 묶어서 main으로 일괄 배포 예정 (옵션 B 선택)

---

## Definition of Done

- [ ] Task 1~11 체크박스 모두 완료
- [ ] `./gradlew build` 성공
- [ ] BoardCommentServiceImplTest 10건 + BoardCommentTest 3건 PASS
- [ ] 기존 BoardCommentControllerTest나 통합 테스트 영향 검토 (있다면 수정)
- [ ] develop 머지 완료
- [ ] PR 1+2 묶어서 main PR (사용자 명시 승인)
- [ ] 프론트팀과 동시 배포 일정 확정

---

## 알려진 제약 / 의도적 미처리 사항

PR 1 코드 리뷰에서 발견한 이슈가 PR 2에서도 동일하게 발생합니다:
- **High (race condition)**: 부모 row lock이 답글 작성 시에도 필요 — 현재 plan은 PR 1 패턴 그대로 복제하므로 같은 버그 함께 옮김. 사용자 결정으로 **나중에 두 도메인 동시 리팩터링**.
- **Medium (N+1)**: `getCommentsByBoard`에서 답글의 `user`/`mentionedUser` LAZY 로딩 → 실제로는 3-쿼리가 아닌 `1+1+1+(N×user)` 발생. 동일 처리 (나중에).
- **Medium (commentCount drift)**: 회원 탈퇴 시 `softDeleteAllByUser` 호출되지만 영향받은 Board들의 `commentCount` 보정 없음. 동일.

향후 리팩터링 시 PetPost와 Board 동시 처리 예정.

---

## Self-review

- **Spec coverage**: spec §3-2(스키마), §4(엔드포인트), §6-1(알림 type), §9-2(테스트 시나리오), §10(배포) 모두 반영. 누락 없음.
- **PR 1과 일관성**: 카운트 메서드 패턴, 엔티티 메서드 명명, 컨트롤러 경로 패턴, DTO 구조 모두 PR 1과 통일.
- **알림 인프라 활용**: PR 1에서 추가한 `relatedPostId`를 BOARD에서 `boardId`로 그대로 사용 — 추가 인프라 작업 없음.
- **Type consistency**: `findByIdForUpdate`, `findParentsByBoardId`, `countByParentAndDeletedAtIsNull` 등 메서드 이름이 task 5에 정의되고 task 7~9에서 그대로 호출됨.
- **Breaking change 명시**: 컨트롤러 경로 변경은 프론트 동시 작업이 전제. PR 본문에 명시.
