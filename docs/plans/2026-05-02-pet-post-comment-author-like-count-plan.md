# 댓글 DTO에 작성자가 받은 좋아요 총개수 필드 추가

## Context
- 댓글 응답에 작성자(유저)가 게시글로 받은 누적 좋아요 합계를 노출.
- 정의는 기존 `UserResponse.totalLikeCount`와 동일 — `PetPost.likeCount`의 작성자별 SUM (게시글 좋아요만, 댓글 좋아요는 도메인 자체가 없음).
- 탈퇴한 사용자는 0으로 강제 (기존 nickname/profileImageUrl 마스킹 정책과 일관).
- 부모 댓글과 답글 양쪽 모두에 노출.

## 결정 사항
- 필드명: `authorTotalLikeCount` (long) — 댓글 자체 좋아요와의 혼동 방지.
- 노출 범위: 부모 + 답글 전부.
- 탈퇴 사용자(`User.isDeleted() == true`): 합계 조회 없이 0 반환.
- N+1 회피: 목록 조회 시 작성자 userId 셋을 모아 batch GROUP BY SUM 쿼리 1회.

## 변경 파일

### 1. `src/main/java/com/jipsamoye/backend/domain/petPost/repository/PetPostRepository.java`
batch 집계 쿼리 추가 (기존 `sumLikeCountByUserId` 바로 아래에).
```java
@Query("SELECT p.user.id, COALESCE(SUM(p.likeCount), 0) " +
       "FROM PetPost p WHERE p.user.id IN :userIds GROUP BY p.user.id")
List<Object[]> sumLikeCountGroupedByUserIds(@Param("userIds") Collection<Long> userIds);
```
- 빈 컬렉션은 호출부에서 차단하고 `Map.of()` 반환 (Spring Data JPA의 IN () 회피).

### 2. `src/main/java/com/jipsamoye/backend/domain/comment/dto/response/CommentResponse.java`
- record 필드 1개 추가: `long authorTotalLikeCount`.
- 정적 팩토리 시그니처 변경:
  - `from(Comment comment, long replyCount, List<CommentResponse> replies, long authorTotalLikeCount)`
  - `ofReply(Comment comment, long authorTotalLikeCount)`
- 탈퇴 사용자 분기 안에서 `authorTotalLikeCount`도 `0L`로 강제 (기존 `isUserDeleted ? ... : ...` 삼항 패턴과 동일하게 처리).

### 3. `src/main/java/com/jipsamoye/backend/domain/comment/service/CommentServiceImpl.java`
4개 호출 지점 수정.

- **`create()` (L94)**: 작성자가 본인 1명. `petPostRepository.sumLikeCountByUserId(userId)` 단일 호출 (탈퇴 유저 분기는 `User.isDeleted()`로 가드 → 본인 작성이므로 사실상 항상 false). `from()`에 전달.
- **`update()` (L113)**: 마찬가지로 작성자 1명. 동일 패턴.
- **`getCommentsByPost()` (L147-176)**: 핵심.
  1. `parents` + 모든 `repliesMap` value를 합쳐 `Set<Long> authorIds` 추출 (탈퇴 유저는 제외).
  2. `authorIds`가 비어있지 않으면 `sumLikeCountGroupedByUserIds(authorIds)` 호출 → `Map<Long, Long> likeMap`.
  3. `parents.map(...)`과 답글 매핑 시 `likeMap.getOrDefault(userId, 0L)` 전달 (탈퇴 유저는 무조건 0).
  4. 기존 `replies.stream().map(CommentResponse::ofReply)`는 람다로 풀어서 likeMap을 캡처.
- **`getReplies()` (L179-183)**: 답글 페이지의 작성자 userId 집합 수집 → 같은 batch 쿼리 1회 → 매핑.
  - `findRepliesByParentId(...)` 결과를 `Page<Comment>`로 받아 content에서 userId 추출 후 `Page#map()` 호출.

## 재사용
- `PetPostRepository.sumLikeCountByUserId(Long)` (L56-57) — 단일 호출용으로 그대로 사용 (create/update).
- `User.isDeleted()` — 탈퇴 분기 가드.
- 기존 batch 패턴 참고: `getCommentsByPost()`의 `countMap` / `repliesMap` 구성 (L158-165).

## 잠재 트레이드오프
- 매번 batch SUM은 사용자가 게시글이 많으면 비용이 들지만, 사이드 프로젝트 규모/현재 게시글 좋아요 수준에서 충분. 캐시/User 카운터 비정규화는 좋아요 토글 트랜잭션 부담과 백필이 필요해 지금 도입은 과함 — 나중 트래픽 증가 시 재검토.
- `sumLikeCountByUserId`/배치 쿼리 둘 다 PetPost soft-delete 조건이 없음 (기존 동작과 동일). 탈퇴 유저의 게시글이 살아있으면 합산되는데, 이번 작업은 작성자 `isDeleted` 가드로 0 처리하므로 영향 없음. 살아있는 유저의 soft-deleted 게시글 합산 여부는 별개 이슈로 두고 본 PR에서 건드리지 않음.

## 테스트
새/수정 단위 테스트 작성 (`./gradlew test` 통과 필수, 커밋 전 검증).

- **`CommentResponseTest`** (없으면 신규):
  - `from()`/`ofReply()`가 `authorTotalLikeCount`를 그대로 직렬화.
  - 탈퇴 사용자일 때 입력 합계와 무관하게 0 출력.
- **`CommentServiceImplTest`** (Mock 기반):
  - `create` → 작성자 합계가 응답에 채워짐 (`sumLikeCountByUserId` 1회 호출).
  - `update` → 동일.
  - `getCommentsByPost` → 부모 N + 답글 M개 작성자가 섞여있을 때 `sumLikeCountGroupedByUserIds`가 **단 1회** 호출되고 결과가 부모/답글 모두에 매핑됨. 탈퇴 작성자 섞이면 그 항목만 0.
  - `getCommentsByPost` 부모가 0건 → batch 쿼리 호출 0회.
  - `getReplies` → 답글 작성자 합계 채워짐.
- **`PetPostRepositoryTest`** (있으면 확장, JPA slice 또는 `@DataJpaTest`):
  - `sumLikeCountGroupedByUserIds`: 여러 유저, 게시글 0건인 유저, 단일 유저 케이스.

## End-to-end 검증
1. `./gradlew build` — 컴파일 + 아키텍처 테스트.
2. `./gradlew test` — 신규 테스트 포함 전체 통과.
3. 로컬 부팅 (`application-local.yaml`) 후 수동 확인:
   - `GET /api/comments/post/{postId}` 응답에 부모/답글 모두 `authorTotalLikeCount` 포함.
   - `POST /api/comments` 응답에 본인 합계 포함.
   - 좋아요가 0인 신규 유저 → 0 반환.
   - 탈퇴 유저 댓글 → 0 반환.

## Out of scope
- User 엔티티에 totalLikeCount 비정규화 (성능 이슈 발생 시 별도 작업).
- 댓글 좋아요 도메인 도입.
- soft-deleted 게시글 합산 여부 정책 변경.
- BoardComment(자유 게시판 댓글) 동일 변경 — 요청에 없으므로 미포함.
