# Phase 1: 대댓글

**Goal:** 유튜브 방식 (depth 1 + @멘션) 대댓글 구현

**Spec:** `docs/superpowers/specs/2026-04-14-social-features-backend-design.md`

**선행:** Phase 0 완료

---

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

---

### Task 1-2: CommentRepository 수정

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/repository/CommentRepository.java`

- [ ] **Step 1: 루트 댓글 조회, 대댓글 조회, replyCount 쿼리 추가**

```java
@Query("SELECT c FROM Comment c WHERE c.petPost = :petPost AND c.parent IS NULL ORDER BY c.createdAt DESC")
Page<Comment> findRootComments(@Param("petPost") PetPost petPost, Pageable pageable);

@Query("SELECT c FROM Comment c WHERE c.parent = :parent AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
Page<Comment> findReplies(@Param("parent") Comment parent, Pageable pageable);

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

---

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

---

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

---

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

---

### Task 1-6: CommentService / CommentServiceImpl 수정

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/service/CommentService.java`
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/service/CommentServiceImpl.java`

- [ ] **Step 1: CommentService 인터페이스에 getReplies 추가**

```java
PageResponse<CommentResponse> getReplies(Long commentId, int page, int size);
```

- [ ] **Step 2: CommentServiceImpl — createComment 수정 (parentId 검증)**

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
        if (parent.getParent() != null) {
            parent = parent.getParent();
        }
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

- [ ] **Step 3: getComments — 루트 댓글만 조회 + replyCount**

```java
@Override
public PageResponse<CommentResponse> getComments(Long postId, int page, int size) {
    PetPost petPost = petPostRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

    Page<CommentResponse> commentPage = commentRepository
            .findRootComments(petPost, PageRequest.of(page, size))
            .map(comment -> {
                long replyCount = commentRepository.countReplies(comment);
                if (comment.getDeletedAt() != null && replyCount == 0) {
                    return null;
                }
                return CommentResponse.from(comment, replyCount);
            });

    List<CommentResponse> filtered = commentPage.getContent().stream()
            .filter(Objects::nonNull)
            .toList();

    return PageResponse.from(new PageImpl<>(filtered, commentPage.getPageable(), commentPage.getTotalElements()));
}
```

- [ ] **Step 4: getReplies 구현**

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

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/comment/service/
git commit -m "feat: 대댓글 생성/조회 서비스 로직 구현"
```

---

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

---

### Task 1-8: Phase 1 배포

- [ ] **Step 1: feature → develop → main PR/머지**
- [ ] **Step 2: feature 브랜치 삭제, develop 최신 동기화**
