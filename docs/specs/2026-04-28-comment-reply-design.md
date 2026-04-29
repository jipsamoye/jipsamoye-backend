# 댓글 답글(대댓글) 도입 설계

> 작성일: 2026-04-28 · 대상: 자랑게시글(PetPost) → 자유게시판(Board) 순차 도입

## 1. 배경

현재 댓글은 평면 구조(`parent_id` 없음)로 자랑게시글·자유게시판 양쪽에 존재. 사용자 피드백으로 답글(대댓글) 요구 누적. 인스타그램·유튜브 패턴 채택해 **1-depth 답글**을 도입한다.

## 2. 결정사항 요약

| 항목 | 결정 | 근거 |
|---|---|---|
| 작업 범위 | 답글(대댓글)만. 좋아요·멘션·이미지 첨부는 v2 | MVP 단순성 |
| 답글 깊이 | 1-depth만 (인스타 패턴) | 모바일 UX, 사진 공유 커뮤니티 적합 |
| 응답 구조 | 부모 + 답글 처음 3개 + "더보기" 별도 API | 페이로드 균형 |
| 부모 삭제 | 답글 있으면 마스킹+답글 유지, 없으면 일반 soft delete | 답글 작성자 글 보존 |
| 카운트 정책 | `commentCount`에 답글 포함 | 전체 활동량 표현 |
| 답글 알림 | 부모 댓글 작성자에게만 (자기 자신 제외) | 인스타 패턴 |
| 답글 정렬 | 오래된 순(작성 순) | 대화 흐름 |
| content 길이 | 부모/답글 동일 max 500자 | 사진 공유 커뮤니티 적합 |
| 알림 type | 도메인별 분리 (PET_POST_COMMENT_REPLY, BOARD_COMMENT_REPLY) | 알림 클릭 이동 명확 |

## 3. 데이터 모델

### 3-1. PR 1: PetPost 작업

**`comments` 테이블 변경:**
```sql
ALTER TABLE comments
  ADD COLUMN parent_id BIGINT NULL,
  ADD COLUMN mentioned_user_id BIGINT NULL,
  ADD COLUMN is_masked BOOLEAN NOT NULL DEFAULT FALSE,
  ADD CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id),
  ADD CONSTRAINT fk_comments_mentioned_user FOREIGN KEY (mentioned_user_id) REFERENCES users(id),
  ADD INDEX idx_comments_post_parent_created (pet_post_id, parent_id, created_at),
  ADD INDEX idx_comments_parent_created (parent_id, created_at);
```

**`pet_post` 테이블 변경:**
```sql
ALTER TABLE pet_post ADD COLUMN comment_count INT NOT NULL DEFAULT 0;

-- 백필 (1회 실행)
UPDATE pet_post p SET comment_count = (
  SELECT COUNT(*) FROM comments c
  WHERE c.pet_post_id = p.id AND c.deleted_at IS NULL
);
```

### 3-2. PR 2: Board 작업

**`board_comments` 테이블 변경**: 동일 패턴 (parent_id, mentioned_user_id, is_masked + 인덱스 2개).
**Board의 `comment_count` 컬럼은 이미 존재** — 별도 작업 없음.

### 3-3. 엔티티 핵심

```java
// Comment / BoardComment 공통 패턴
@SQLRestriction("deleted_at IS NULL")
public class Comment extends BaseEntity {
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "parent_id")
    private Comment parent;  // null = 부모 댓글

    @ManyToOne(fetch = LAZY) @JoinColumn(name = "mentioned_user_id")
    private User mentionedUser;  // 답글의 답글 시 원 답글 작성자

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;  // 원본 영구 보존 (마스킹은 응답 단계)

    private boolean isMasked;
    private LocalDateTime deletedAt;

    public void mask() { this.isMasked = true; }
    public void softDelete() { this.deletedAt = LocalDateTime.now(); }
    public boolean isReply() { return parent != null; }
}
```

## 4. API 엔드포인트

### PR 1 (PetPost)
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/comments/post/{postId}` | 부모 댓글 페이지 + 각 부모의 답글 처음 3개 |
| GET | `/api/comments/{parentId}/replies` | 답글 더보기 (offset, 오래된 순) |
| POST | `/api/comments` | 댓글/답글 작성 |
| PUT | `/api/comments/{id}` | 수정 (작성자 본인만) |
| DELETE | `/api/comments/{id}` | 삭제 (작성자 본인만) |

### PR 2 (Board): 동일 구조의 `/api/board-comments/...` 엔드포인트.

### 4-1. 요청 DTO
```java
public record CommentCreateRequest(
    @NotNull Long petPostId,
    Long parentId,             // null OK
    Long mentionedUserId,      // null OK
    @NotBlank @Size(max = 500) String content
) {}
```

### 4-2. 응답 DTO
```java
public record CommentResponse(
    Long id,
    String content,                    // isMasked면 null (클라이언트가 문구 결정)
    String nickname,                   // 탈퇴 유저면 "탈퇴한 사용자"
    String profileImageUrl,
    String mentionedNickname,          // null OK
    boolean isMasked,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long replyCount,                   // 답글이면 0
    List<CommentResponse> replies      // 부모 댓글의 처음 3개
) {}
```

## 5. 핵심 로직

### 5-1. 작성 흐름
1. 사용자/게시글 검증
2. `parentId != null`이면 parent 검증:
   - 같은 게시글 소속 확인
   - **마스킹된 부모면 거부** (BadRequest)
   - parent가 답글(`isReply()`)이면 → 부모를 root로 자동 매핑 + `mentionedUser`에 원 답글 작성자 보존
3. 댓글 INSERT + `incrementCommentCount(postId)`
4. parent.user != 작성자면 `ReplyAddedEvent` 발행

### 5-2. 삭제 흐름
1. **`findByIdForUpdate(id)`로 row lock** (race condition 방어)
2. 작성자 검증
3. 분기:
   - **답글 삭제**: 일반 soft delete + `commentCount −1`. 부모가 마스킹 상태였고 마지막 답글이면 부모도 soft delete + `commentCount −1` 추가 (cascade)
   - **부모 댓글 삭제 (답글 있음)**: `mask()` 호출 — `isMasked=true`만, content는 원본 보존, 카운트 변동 없음
   - **부모 댓글 삭제 (답글 없음)**: 일반 soft delete + `commentCount −1`

### 5-3. 부모 댓글 페이지 + 답글 처음 3개 동봉 조회

**N+1 회피 — 3단 쿼리:**
1. 부모 댓글 페이지 (`pet_post_id` + `parent_id IS NULL`)
2. 답글 카운트 그룹: `WHERE parent_id IN (:ids) GROUP BY parent_id`
3. 답글 처음 3개 — MySQL 8.0 윈도우 함수 native query:
```sql
SELECT * FROM (
  SELECT c.*, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY created_at ASC) AS rn
  FROM comments c
  WHERE c.parent_id IN (:parentIds) AND c.deleted_at IS NULL
) t WHERE t.rn <= 3
```

### 5-4. PetPost 카운트 atomic update
```java
@Modifying @Query("UPDATE PetPost p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
void incrementCommentCount(@Param("id") Long id);

@Modifying @Query("UPDATE PetPost p SET p.commentCount = p.commentCount - 1 WHERE p.id = :id AND p.commentCount > 0")
void decrementCommentCount(@Param("id") Long id);
```

## 6. 알림

### 6-1. NotificationType 추가
- PR 1: `PET_POST_COMMENT_REPLY`
- PR 2: `BOARD_COMMENT_REPLY`

### 6-2. 이벤트 패턴
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Transactional(propagation = REQUIRES_NEW)
public void handle(ReplyAddedEvent event) { ... }
```
- receiver: parent.user
- sender: 답글 작성자
- 자기 자신 답글이면 이벤트 미발행

## 7. 트랜잭션·동시성

| 작업 | 트랜잭션 |
|---|---|
| 댓글 작성 | INSERT + commentCount UPDATE + 이벤트 발행 (한 트랜잭션) |
| 댓글 삭제 | `findByIdForUpdate` row lock + soft delete + 카운트 조정 |
| 알림 생성 | AFTER_COMMIT + REQUIRES_NEW (기존 패턴) |

**동시성 시나리오 방어:**
- 부모 삭제 vs 답글 추가 동시 발생: 부모의 row lock으로 직렬화
- 답글 작성 시 부모의 `deleted_at IS NOT NULL` 또는 `is_masked = true`이면 거부

## 8. 검증

| 필드 | 제약 |
|---|---|
| `content` | NotBlank, max 500자 |
| `parentId` | optional, 존재·같은 게시글 검증, 마스킹 부모 거부 |
| `mentionedUserId` | optional, 존재 검증 |

## 9. 테스트 계획

### 9-1. CommentServiceImplTest (PR 1)
- 부모 댓글 작성 정상
- 답글 작성 정상 + 부모 작성자에게 알림 발행
- 답글의 답글 시도 → root 자동 매핑 + mentionedUser 보존
- 마스킹된 부모에 답글 → BadRequest
- 부모 삭제(답글 없음) → 일반 soft delete + 카운트 −1
- 부모 삭제(답글 있음) → 마스킹, 카운트 변동 없음
- 마지막 답글 삭제 → 부모도 soft delete + 카운트 −2
- 자기 자신 답글 → 알림 미발행

### 9-2. BoardCommentServiceImplTest (PR 2): 동일 시나리오 복제

## 10. 배포·마이그레이션

### PR 1 배포 순서
1. DB 변경 (`comments` 컬럼·인덱스 추가, `pet_post.comment_count` 추가, 백필 SQL)
2. 백엔드 배포
3. 프론트 배포 (응답 구조 변경 — `replies`, `mentionedNickname` 등 신규 필드 처리)

`application-prod.yaml`이 `validate`로 운영 중이라면 별도 ALTER SQL 실행 후 백엔드 배포.

### PR 2 배포 순서
PR 1과 동일 패턴.

## 11. PR 분리 계획

### PR 1: PetPost 대댓글 도입
- **목표**: 댓글 답글 패턴 확립 + 검증
- **범위**: `comments`, `pet_post` 스키마 + Comment 도메인 + 알림 type 1개
- **머지 후**: 운영에서 1~2주 안정성 모니터링

### PR 2: Board 대댓글 도입
- **목표**: PR 1 패턴을 자유게시판으로 복제
- **범위**: `board_comments` 스키마 + BoardComment 도메인 + 알림 type 1개
- **선결 조건**: PR 1 안정 운영 확인

## 12. 향후 검토 사항 (이번 작업 스코프 외)

- 댓글/좋아요 도메인 통합 (`Comment`+`BoardComment` → polymorphic) — 4개 도메인 관리 부담 임계점 도달 시 검토
- 댓글 좋아요 — 인기 댓글 정렬 도입 시
- 멘션(@닉네임) 자동 링크 + 알림 — 텍스트 기반 사용자 검색 인프라 갖춘 후
- 댓글 이미지 첨부 — `ImageService`와 연동
- 답글 페이지네이션 cursor 전환 — 답글이 100개 이상 달리는 게시글 등장 시
