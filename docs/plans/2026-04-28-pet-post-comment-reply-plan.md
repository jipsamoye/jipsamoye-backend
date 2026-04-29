# PetPost 대댓글 도입 Implementation Plan (PR 1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`).

**Goal:** 자랑게시글(PetPost)에 1-depth 답글 도입 — `parent_id` self-ref + 마스킹 + N+1 회피 + 답글 알림.

**Architecture:** `Comment`에 `parent`, `mentionedUser`, `isMasked` 추가. 부모 댓글 페이지 + 각 부모의 답글 처음 3개 동봉. 부모 삭제 시 답글 있으면 마스킹(content 보존)·없으면 일반 soft delete. PetPost에 `commentCount` 컬럼 신규 + atomic update.

**Tech Stack:** Spring Boot 3.5.13, Java 17, MySQL 8.0(윈도우 함수), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/specs/2026-04-28-comment-reply-design.md` 참조 — 결정사항·DTO 스키마 등 디테일은 spec에 있음.

---

## File Structure

### 신규 파일
- `src/main/resources/db/migration/2026-04-28-pet-post-comment-reply.sql` — 운영 DB ALTER 스크립트
- `src/test/java/.../comment/entity/CommentTest.java` — 비즈니스 메서드 단위 테스트
- `src/test/java/.../comment/service/CommentServiceImplTest.java` — Mockito 서비스 테스트

### 수정 파일

| 파일 | 변경 요약 |
|---|---|
| `notification/entity/NotificationType.java` | `PET_POST_COMMENT_REPLY` 추가 |
| `petPost/entity/PetPost.java` | `commentCount` 필드 |
| `petPost/repository/PetPostRepository.java` | `incrementCommentCount`, `decrementCommentCount` |
| `petPost/dto/response/PetPostResponse.java` | `commentCount` 필드 노출 |
| `petPost/dto/response/PetPostListResponse.java` | `commentCount` 필드 노출 |
| `comment/entity/Comment.java` | `parent`, `mentionedUser`, `isMasked` + `mask()`, `isReply()` |
| `comment/repository/CommentRepository.java` | 답글 쿼리·row lock·count |
| `comment/dto/request/CommentCreateRequest.java` | `parentId`, `mentionedUserId`, `@Size(max=500)` |
| `comment/dto/request/CommentUpdateRequest.java` | `@Size(max=500)` |
| `comment/dto/response/CommentResponse.java` | `mentionedNickname`, `isMasked`, `replyCount`, `replies` |
| `comment/service/CommentService.java` | 새 메서드 시그니처 |
| `comment/service/CommentServiceImpl.java` | 답글 흐름 (작성/삭제/조회) |
| `comment/controller/CommentController.java` | 부모+답글 조회·답글 더보기 엔드포인트 |

---

## Task 1: DB 마이그레이션 SQL 작성

**Files:** Create `src/main/resources/db/migration/2026-04-28-pet-post-comment-reply.sql`

내용: ① `comments` 테이블에 `parent_id`(FK→comments.id), `mentioned_user_id`(FK→users.id), `is_masked BOOLEAN NOT NULL DEFAULT FALSE` 추가. ② 인덱스 2개 — `idx_comments_post_parent_created (pet_post_id, parent_id, created_at)`, `idx_comments_parent_created (parent_id, created_at)`. ③ `pet_post`에 `comment_count INT NOT NULL DEFAULT 0` 추가. ④ 백필 SQL — 기존 댓글 수 집계해서 `comment_count` 채우기.

- [ ] 디렉토리 생성 (`mkdir -p src/main/resources/db/migration`)
- [ ] SQL 파일 작성
- [ ] 커밋 — `docs: 대댓글 도입 DB 마이그레이션 SQL 추가`

---

## Task 2: NotificationType.PET_POST_COMMENT_REPLY 추가

**Files:** Modify `notification/entity/NotificationType.java`

기존 enum에 `PET_POST_COMMENT_REPLY` 한 줄 추가.

- [ ] enum 값 추가
- [ ] `./gradlew compileJava` 통과 확인
- [ ] 커밋 — `feat: NotificationType에 PET_POST_COMMENT_REPLY 추가`

---

## Task 3: PetPost commentCount 인프라 (엔티티 + Repository)

**Files:** Modify `petPost/entity/PetPost.java`, `petPost/repository/PetPostRepository.java`

PetPost: `private int commentCount = 0;` 필드. `@Column(nullable = false)`. setter 없음.

PetPostRepository에 두 메서드 — `@Modifying` JPQL UPDATE atomic:
- `void incrementCommentCount(Long id)`
- `void decrementCommentCount(Long id)` — `WHERE id = :id AND commentCount > 0` (음수 방지 가드)

- [ ] PetPost 필드 추가
- [ ] Repository 메서드 추가
- [ ] `./gradlew compileJava` 통과
- [ ] 커밋 — `feat: PetPost commentCount 컬럼 + atomic update 메서드`

---

## Task 4: Comment 엔티티 확장 + 비즈니스 메서드 + 단위 테스트 (TDD)

**Files:** Modify `comment/entity/Comment.java`, Create `src/test/java/.../comment/entity/CommentTest.java`

엔티티 변경:
- `@ManyToOne(fetch=LAZY) @JoinColumn(name="parent_id") private Comment parent`
- `@ManyToOne(fetch=LAZY) @JoinColumn(name="mentioned_user_id") private User mentionedUser`
- `@Column(nullable=false) private boolean isMasked = false`
- `@Table(indexes = { ... })` 인덱스 2개 (DDL은 마이그레이션 SQL이 진실 원천, 매핑 일관성용)
- `@Builder` 시그니처에 `parent`, `mentionedUser` 추가
- 비즈니스 메서드: `void mask()` (`isMasked=true`만), `boolean isReply()` (`parent != null`)

단위 테스트 (CommentTest):
- `isReply_distinguishesParentAndChild` — parent null/non-null
- `mask_setsFlagWithoutAlteringContent` — content 원본 보존 검증
- `isMasked_defaultsFalse` — 기본값 false

- [ ] 단위 테스트 작성 (failing)
- [ ] `./gradlew test --tests "*CommentTest"` → FAIL 확인
- [ ] 엔티티 구현
- [ ] 테스트 PASS 확인
- [ ] 커밋 — `feat: Comment 엔티티에 parent·mentionedUser·isMasked 추가`

---

## Task 5: CommentRepository 답글 쿼리 추가

**Files:** Modify `comment/repository/CommentRepository.java`

추가할 메서드 시그니처:
- `Page<Comment> findParentsByPetPostId(Long postId, Pageable)` — JPQL `WHERE petPost.id = :postId AND parent IS NULL ORDER BY createdAt DESC`
- `List<Object[]> countRepliesGroupedByParentIds(List<Long> parentIds)` — `SELECT parent.id, COUNT(c) GROUP BY parent.id`
- `List<Comment> findTop3RepliesByParentIds(List<Long> parentIds)` — **native query (MySQL 8.0 윈도우 함수, 다음 코드 블록)**
- `Page<Comment> findRepliesByParentId(Long parentId, Pageable)` — `ORDER BY createdAt ASC`
- `long countByParentAndDeletedAtIsNull(Comment parent)` — Spring Data 메서드 이름
- `Optional<Comment> findByIdForUpdate(Long id)` — `@Lock(LockModeType.PESSIMISTIC_WRITE)`

윈도우 함수 native query (이건 외워 쓰기 어려우니 그대로 사용):
```sql
SELECT * FROM (
  SELECT c.*, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY created_at ASC) AS rn
  FROM comments c
  WHERE c.parent_id IN (:parentIds) AND c.deleted_at IS NULL
) t WHERE t.rn <= 3
```
> `@SQLRestriction`은 native query에 적용 안 되므로 `deleted_at IS NULL` 명시 필터 필수.

- [ ] Repository 메서드 추가
- [ ] `./gradlew compileJava` 통과
- [ ] 커밋 — `feat: CommentRepository에 답글 조회·row lock·count 메서드 추가`

---

## Task 6: Comment DTO 갱신 (요청/응답)

**Files:** Modify `comment/dto/request/CommentCreateRequest.java`, `comment/dto/request/CommentUpdateRequest.java`, `comment/dto/response/CommentResponse.java`

- `CommentCreateRequest`: `(Long petPostId, Long parentId, Long mentionedUserId, String content)` — `@NotNull`, `@NotBlank @Size(max=500)`
- `CommentUpdateRequest`: `@NotBlank @Size(max=500)`
- `CommentResponse`: 기존 필드 + `mentionedNickname`(nullable), `isMasked`, `replyCount`, `replies(List<CommentResponse>)`. 정적 팩토리 두 개:
  - `from(Comment, long replyCount, List<CommentResponse> replies)` — 부모용. 마스킹 시 `content=null`. 탈퇴 유저 마스킹은 기존 패턴 그대로
  - `ofReply(Comment)` — 답글용 (replyCount=0, replies=List.of())

> Service 변경(Task 7~9)에서 기존 `from(Comment)` 호출처를 모두 갱신하므로 일시적으로 컴파일 깨짐.

- [ ] DTO 3개 갱신
- [ ] 컴파일 깨짐 확인 (Task 7에서 정리)
- [ ] **이 단계는 별도 커밋 X** — Task 7 커밋에 묶음

---

## Task 7: CommentService.create — 답글 작성 (TDD)

**Files:** Modify `comment/service/CommentService.java`, `comment/service/CommentServiceImpl.java`, Create `src/test/java/.../comment/service/CommentServiceImplTest.java`

### 인터페이스 시그니처 갱신
```java
CommentResponse create(CommentCreateRequest request, Long userId);
CommentResponse update(Long commentId, CommentUpdateRequest request, Long userId);
void delete(Long commentId, Long userId);
PageResponse<CommentResponse> getCommentsByPost(Long postId, Pageable pageable);
PageResponse<CommentResponse> getReplies(Long parentId, Pageable pageable);
```
update/delete/getCommentsByPost/getReplies는 이번 task에선 `throw new UnsupportedOperationException()` 두고 Task 8~10에서 구현.

### create 로직
1. user, post 검증
2. `parentId != null`이면:
   - parent fetch + 같은 게시글 검증 + 마스킹 검증 (마스킹이면 `BusinessException(BAD_REQUEST, "삭제된 댓글에는 답글을 달 수 없습니다.")`)
   - parent가 답글이면 (`parent.isReply()`) → **부모를 root로 자동 매핑** + `mentionedUser = parent.getUser()`
   - parent가 부모면 → 그대로 사용 + `mentionedUserId`가 있으면 fetch해서 mentionedUser 세팅
3. Comment 저장 + `petPostRepository.incrementCommentCount(postId)`
4. `parent != null && parent.user.id != userId`면 `eventPublisher.publishEvent(new NotificationEvent(...))` — type=PET_POST_COMMENT_REPLY, message=`"닉네임님이 회원님의 댓글에 답글을 남겼습니다"`

> `NotificationEvent` 시그니처: `(receiver, sender, type, targetId, message)` — 기존 `LikeServiceImpl` 패턴 동일.

### 단위 테스트 (CommentServiceImplTest)
Mockito + `ReflectionTestUtils.setField(entity, "id", N)`으로 ID 주입.
- `create_reply_publishesNotificationEvent` — ArgumentCaptor로 NotificationEvent 검증 (type, receiver, sender)
- `create_selfReply_doesNotPublishEvent` — sender == receiver면 미발행
- `create_replyToReply_remapsRootAndPreservesMention` — 답글의 답글 시 saved.parent == root, saved.mentionedUser == 원답글작성자
- `create_replyToMaskedParent_throwsException` — `BusinessException` + `commentRepository.save` 미호출

### 의존성
`@RequiredArgsConstructor`로 주입: `CommentRepository`, `PetPostRepository`, `UserRepository`, `ApplicationEventPublisher`. Service는 `@Transactional(readOnly=true)` 클래스 + `create`/`delete`/`update`에 `@Transactional` 오버라이드.

- [ ] 인터페이스 갱신
- [ ] 테스트 4건 작성 (failing)
- [ ] `./gradlew test --tests "*CommentServiceImplTest"` → FAIL
- [ ] create 구현 (다른 메서드는 UnsupportedOperationException)
- [ ] 테스트 PASS
- [ ] 커밋 — `feat: CommentServiceImpl 답글 작성 로직 (1-depth 강제, 마스킹 거부, 알림)`

---

## Task 8: CommentService.delete — 마스킹·cascade (TDD)

**Files:** Modify `comment/service/CommentServiceImpl.java`, Modify `CommentServiceImplTest.java`

### delete 로직
1. `commentRepository.findByIdForUpdate(commentId)` — row lock
2. 작성자 검증 (다르면 `FORBIDDEN`)
3. 분기:
   - `comment.isReply()` → `comment.softDelete()` + `decrementCommentCount`. 부모가 마스킹이고 `countByParentAndDeletedAtIsNull(parent) == 0`이면 부모도 `softDelete()` + `decrementCommentCount` 추가 (cascade)
   - 부모 댓글이고 답글 있음 (`countByParentAndDeletedAtIsNull(comment) > 0`) → `comment.mask()` (카운트 변동 없음)
   - 부모 댓글이고 답글 없음 → `comment.softDelete()` + `decrementCommentCount`

### 추가 단위 테스트
- `delete_parentWithoutReplies_softDeletesAndDecrements` — deletedAt != null + isMasked=false + decrement 1회
- `delete_parentWithReplies_masksAndKeepsCount` — isMasked=true + deletedAt=null + decrement never
- `delete_lastReplyOfMaskedParent_cascadesParentDelete` — reply.deletedAt != null + parent.deletedAt != null + decrement 2회 (`verify(petPostRepository, times(2))`)
- `delete_otherUsersComment_throwsForbidden`

- [ ] 테스트 4건 추가 (failing)
- [ ] FAIL 확인
- [ ] delete 구현
- [ ] 테스트 PASS (총 8건)
- [ ] 커밋 — `feat: CommentServiceImpl 삭제 로직 (마스킹·cascade·row lock)`

---

## Task 9: CommentService.getCommentsByPost / getReplies — N+1 회피 조회 (TDD)

**Files:** Modify `comment/service/CommentServiceImpl.java`, Modify `CommentServiceImplTest.java`

### getCommentsByPost 로직 (3-쿼리 N+1 회피)
1. `commentRepository.findParentsByPetPostId(postId, pageable)` — 부모 페이지
2. parentIds 추출. 비어있으면 빈 페이지 매핑 후 즉시 반환
3. `countRepliesGroupedByParentIds(parentIds)` → `Map<Long, Long>`
4. `findTop3RepliesByParentIds(parentIds)` → `Map<Long, List<Comment>>` (Java `Collectors.groupingBy(r -> r.getParent().getId())`)
5. `parents.map(p -> CommentResponse.from(p, countMap.getOrDefault(...0L), repliesMap.getOrDefault(...List.of()).map(ofReply)))`
6. `PageResponse.from(...)` 래핑

### getReplies 로직
- `commentRepository.findRepliesByParentId(parentId, pageable).map(CommentResponse::ofReply)` → `PageResponse.from`

### 단위 테스트
- `getCommentsByPost_attachesRepliesAndCount` — 부모 2개, 첫 부모에 답글 2개 → 첫 응답 replyCount=2, replies.size=2; 두 번째 응답 replyCount=0, replies 빈 리스트
- `getReplies_returnsOldestFirstPage` — 단일 답글 페이지 검증

- [ ] 테스트 2건 추가 (failing)
- [ ] FAIL 확인
- [ ] 두 메서드 구현
- [ ] 테스트 PASS (총 10건)
- [ ] 커밋 — `feat: CommentServiceImpl 부모+답글 동봉 조회 + 답글 더보기 (N+1 회피)`

---

## Task 10: CommentService.update + Controller 엔드포인트

**Files:** Modify `comment/service/CommentServiceImpl.java`, `comment/controller/CommentController.java`

### update 로직
- `findById` (lock 불필요) → 작성자 검증 → 마스킹 검증 (마스킹이면 `BAD_REQUEST`) → `comment.updateContent(...)`

### Controller 엔드포인트 (전체 재작성)
- `POST /api/comments` → `create` (`@AuthenticationPrincipal CustomUserDetails`)
- `PATCH /api/comments/{commentId}` → `update`
- `DELETE /api/comments/{commentId}` → `delete`
- `GET /api/comments/post/{postId}?page=&size=` → `getCommentsByPost` (size `@Min(1) @Max(50)`)
- `GET /api/comments/{parentId}/replies?page=&size=` → `getReplies` (size `@Min(1) @Max(50)`)

`@RestController @RequestMapping("/api/comments") @Validated` + Swagger `@Tag/@Operation`. 응답은 `ResponseEntity<ApiResponse<...>>`. POST는 201 Created (`ApiResponse.created`), 나머진 200.

- [ ] update 구현
- [ ] Controller 전체 갱신
- [ ] `./gradlew build` 통과 확인
- [ ] 커밋 — `feat: CommentController 답글 엔드포인트 + update 로직`

---

## Task 11: PetPost 응답 DTO에 commentCount 노출

**Files:** Modify `petPost/dto/response/PetPostResponse.java`, `petPost/dto/response/PetPostListResponse.java`, Modify `src/test/.../PetPostListResponseTest.java`

두 record에 `int commentCount` 필드 추가 (likeCount 옆 위치). `from(PetPost)` 메서드에서 `petPost.getCommentCount()` 매핑.

PetPostListResponseTest에 한 줄 추가 — `assertThat(response.commentCount()).isZero();` (기본값 0 검증).

> 영향: PetPostListResponse는 9개 API가 공유. 모든 응답에 commentCount가 자동 노출됨. 프론트팀에 공지 필요 (운영 노트).

- [ ] 두 DTO 갱신
- [ ] 기존 테스트 보강
- [ ] `./gradlew test` 모든 테스트 PASS
- [ ] 커밋 — `feat: PetPost 응답 DTO에 commentCount 노출`

---

## Task 12: 빌드 검증 + ArchUnit + PR 생성

**Files:** (변경 없음)

- [ ] `./gradlew build` 전체 통과
- [ ] `./gradlew test --tests "*ArchitectureTest"` — ArchUnit 컨벤션 검증 (DTO 위치, @Setter 금지, 레이어 의존 방향)
- [ ] develop으로 push
- [ ] **사용자 확인 후** develop → main PR 생성. PR 본문에 다음 명시:
  - 백/프론트 동시 배포 필요 (응답 구조 변경)
  - 운영 DB 수동 마이그레이션 SQL 1회 실행 필요
  - 백필 SQL 검증 후 백엔드 배포

---

## Definition of Done

- [ ] Task 1~12 체크박스 모두 완료
- [ ] `./gradlew build` 성공
- [ ] CommentServiceImplTest 10건 + CommentTest 3건 PASS
- [ ] spec(`docs/specs/2026-04-28-comment-reply-design.md`)의 결정사항 9개 모두 구현 확인
- [ ] develop 머지 완료
- [ ] 프론트팀과 동시 배포 일정 확정
- [ ] main PR 머지 (사용자 명시 승인)
- [ ] 운영 1~2주 안정성 확인 후 PR 2 (자유게시판) 작업 착수

---

## Self-review

- **Spec coverage**: spec 결정사항 9개 → Task 1(스키마), Task 2(알림 type), Task 3(commentCount 인프라), Task 4(엔티티 + 비즈니스 메서드), Task 5(N+1 회피 쿼리), Task 6(DTO), Task 7(작성·1-depth·마스킹 거부·알림), Task 8(삭제·마스킹·cascade), Task 9(조회·답글 동봉), Task 10(update·controller·offset), Task 11(commentCount 노출). 누락 없음.
- **Placeholder scan**: "TBD"/"TODO" 없음. 코드 미세부는 spec 참조로 위임.
- **Type consistency**: `findByIdForUpdate`, `findParentsByPetPostId`, `countByParentAndDeletedAtIsNull` 등 메서드 이름이 task 5에 정의되고 task 7~9에서 그대로 호출됨. `CommentResponse.from(...)`, `CommentResponse.ofReply(...)` 두 시그니처도 task 6→7~9에서 일관.
- **알려진 제약**: 댓글 작성 알림(`COMMENT` type)은 현재 NotificationType에 없음. 이번 PR 스코프 외 — 답글 알림만 도입. 추후 별도 작업.
