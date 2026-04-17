# 집사모여 백엔드 개선 계획서

> 2026-04-16 코드리뷰 기반 작성

## 종합 점수: 6.2/10

| 영역 | 점수 | 상태 |
|:--|:--:|:--|
| 아키텍처 & 구조 | 8/10 | 좋음 |
| API 설계 | 7/10 | 양호 |
| 엔티티 설계 | 8/10 | 좋음 |
| 보안 | 5/10 | 심각 |
| 에러 처리 | 7/10 | 양호 |
| 성능 | 4/10 | 심각 |
| 테스트 | 3/10 | 심각 |
| 코드 품질 | 6/10 | 보통 |
| 인프라 & 배포 | 7/10 | 양호 |
| 모니터링 | 3/10 | 심각 |

---

## Phase 1: 긴급 수정 (보안 + 성능)

### 1-1. JWT/OAuth2 인증 구현
- **문제**: `SecurityConfig`에서 `anyRequest().permitAll()`, 모든 API가 `@RequestParam Long userId`로 사용자 식별 → 누구나 다른 사용자 행세 가능
- **영향 범위**: 20개 이상의 컨트롤러 메서드
- **해결**: Spring Security + JWT 인증 구현, SecurityContext에서 userId 추출
- **관련 파일**:
  - `global/config/SecurityConfig.java:25`
  - `auth/controller/AuthController.java:33` (TODO 주석 존재)
  - 모든 Controller의 `@RequestParam Long userId` 제거

### 1-2. N+1 쿼리 수정
- **문제 1**: 유저 프로필 조회 시 4번 쿼리 (유저 + 게시글수 + 팔로워수 + 팔로잉수)
  - `user/service/UserServiceImpl.java:40-42`
  - **해결**: 단일 집계 쿼리 또는 `@Query`로 한 번에 조회
- **문제 2**: DM 목록 조회 시 N개 방마다 2번 추가 쿼리 (마지막 메시지 + 읽지 않은 수)
  - `dm/service/DmServiceImpl.java:36-52`
  - **해결**: JOIN FETCH 또는 배치 쿼리
- **문제 3**: 게시글 목록에서 User LAZY 로딩으로 N+1
  - **해결**: `@EntityGraph(attributePaths = {"user"})` 추가

### 1-3. WebSocket CORS 수정
- **문제**: REST CORS는 도메인 제한했지만 WebSocket은 `setAllowedOriginPatterns("*")`
- **관련 파일**: `global/config/WebSocketConfig.java:22`
- **해결**: REST CORS와 동일한 도메인 목록 적용

### 1-4. 페이지 크기 제한
- **문제**: `@RequestParam(defaultValue = "20") int size`에 최대값 제한 없음 → size=10000000 요청 시 OOM
- **해결**: `@Max(100)` 어노테이션 추가 또는 서비스에서 제한

---

## Phase 2: 안정성 강화 (테스트 + 모니터링)

### 2-1. CI에서 테스트 활성화
- **문제**: `ci.yml:27`, `deploy.yml:25`에서 `-x test`로 테스트 스킵 → CI의 의미 없음
- **해결**: `-x test` 제거, 테스트 실패 시 빌드 실패하도록 변경

### 2-2. 테스트 코드 추가 (목표: 70% 커버리지)
- **현재**: 100개 파일 중 테스트 6개 (6%)
- **테스트 없는 서비스**: LikeService, CommentService, FollowService, AuthService, ImageService, PetPostService
- **우선순위**:
  1. PetPostServiceImpl (게시글 CRUD + 캐스케이드 삭제)
  2. AuthServiceImpl (게스트 생성 + 회원 탈퇴)
  3. LikeServiceImpl (토글 로직 + 레이스 컨디션)
  4. FollowServiceImpl (토글 + 알림 이벤트)
  5. CommentServiceImpl (CRUD + soft delete)

### 2-3. Prometheus scrape 타겟 확인
- **현재**: `monitoring/prometheus/prometheus.yml:10` — `jipsamoye-nginx:80`을 scrape
- **확인**: 이전 커밋에서 의도적으로 Nginx 경유 scrape로 변경한 이력 있음. Nginx를 통해 `/actuator/prometheus` 접근 가능하면 정상
- **해결**: Nginx 경유가 메트릭 수집에 문제없는지 검증, 문제 시 앱 직접 scrape로 변경

### 2-4. Nginx 보안 헤더 추가
- **문제**: 보안 관련 헤더 없음
- **관련 파일**: `nginx/nginx.conf`
- **추가할 헤더**:
  ```nginx
  add_header X-Frame-Options "SAMEORIGIN" always;
  add_header X-Content-Type-Options "nosniff" always;
  ```

---

## Phase 3: 코드 품질 개선

### 3-1. User 엔티티 `@SQLRestriction` 추가
- **문제**: PetPost, Comment에는 `@SQLRestriction("deleted_at IS NULL")`이 있지만 User에는 없음 → 탈퇴 유저 조회 가능
- **관련 파일**: `user/entity/User.java`
- **주의**: UserServiceImpl에서 `user.isDeleted()` 수동 체크하는 부분과의 호환 확인 필요

### 3-2. DB 인덱스 추가
- **누락된 인덱스**: 엔티티에 `@Index` 없음
- **추가 대상**:
  - `User.nickname` (프로필 조회)
  - `PetPost.createdAt` (목록 정렬)
  - `Follow.follower_id`, `Follow.following_id` (카운트 조회)
  - `DmMessage.room_id` (채팅방 메시지 조회)
  - `Notification.receiver_id` (알림 목록)

### 3-3. DRY 개선: 유저 조회 헬퍼
- **문제**: `userRepository.findById(userId).orElseThrow(...)` 패턴이 30회 이상 반복
- **해결**: 공통 메서드 추출
  ```java
  // UserService에 추가
  User getOrThrow(Long userId);
  User getByNicknameOrThrow(String nickname);
  ```

### 3-4. S3 이미지 정리 로직 완성
- **문제**: 회원 탈퇴, 게스트 정리 시 S3 이미지 미삭제 (TODO 주석만 존재)
- **관련 파일**:
  - `auth/service/AuthServiceImpl.java:81`
  - `global/scheduler/GuestCleanupScheduler.java:49`
- **해결**: 유저의 게시글 이미지 + 프로필/커버 이미지 S3 삭제 로직 추가

### 3-5. Dockerfile 개선
- **추가할 사항**:
  - `HEALTHCHECK` 지시어 추가
  - root 대신 일반 유저로 실행 (`USER appuser`)
  - Docker 이미지 버전 고정 (`:latest` → 특정 버전)

---

## Phase 4: 고도화

### 4-1. 캐싱 도입
- 유저 프로필, 인기 게시글 등 자주 조회되는 데이터 캐싱
- `PopularPostScheduler`가 현재 비활성화(주석 처리) → 활성화 검토

### 4-2. 댓글 알림 추가
- 현재 좋아요/팔로우만 알림, 댓글 알림 미구현
- `NotificationType`에 `COMMENT` 추가

### 4-3. API 버전 관리
- 현재 `/api/posts` → `/api/v1/posts`로 변경 검토
- 프론트엔드와 협의 필요

### 4-4. Rate Limiting
- API 요청 속도 제한 (스팸/남용 방지)
- Nginx 또는 Spring에서 구현

### 4-5. DB 마이그레이션 도구 도입
- 현재 `ddl-auto: update` 사용 → 운영에서 위험
- Flyway 또는 Liquibase 도입 검토

---

## 완료된 항목 (2026-04-16)

- [x] Cloudflare CDN 이미지 서빙 적용
- [x] 디스코드 에러 알림에 요청 정보 추가 (URL, IP, User-Agent)
- [x] 팔로우 알림 중복 체크 버그 수정
- [x] 알림 전송 TransactionalEventListener로 개선
