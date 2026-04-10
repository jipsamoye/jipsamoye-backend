# 집사모여 — 구현 계획 (PLAN)

> **기반 문서:** [PRD](./PRD.md), [TRD](./TRD.md)
> **작성일:** 2026-04-10
> **범위:** 백엔드 (프론트엔드는 백엔드 완성 후 별도 레포에서 진행)

---

## 현재 상태

**이미 구현된 ��:**
- Spring Boot 프로젝트 기본 세팅 (`BackendApplication.java` — `@EnableJpaAuditing` 적용됨)
- `BaseEntity` (createdAt, updatedAt — `@CreatedDate`, `@LastModifiedDate`)
- `PetPost` 엔티티 (id, title, content — 필드 확장 예정)
- `HealthController` (헬스체크 — 유지)
- application.yaml 프로필 분리 (local, prod)
- Docker, Nginx, GitHub Actions CI/CD 구성 완료
- Swagger (springdoc-openapi) 적용됨
- build.gradle: JPA, Validation, Web, Swagger, Lombok, MySQL

**구현 필요:**
- 전체 도메인 로직 (User, PetPost, Comment, Like, Follow, Auth, Image)
- Spring Security + OAuth2 + 세션
- S3 Presigned URL
- 공통 응답/에러 처리
- 스케줄러

---

## 서비스 ��층 규���

모든 도메인의 Service는 **인터페이스 + 구현체**로 ���리한다.

```
domain/{도메인}/service/
├��─ {Domain}Service.java        // 인터페이스
└── {Domain}ServiceImpl.java    // 구현체
```

예시:
```java
public interface PetPostService {
    PetPostResponse createPost(PetPostCreateRequest request, Long userId);
    PetPostResponse getPost(Long id);
    // ...
}

@Service
@RequiredArgsConstructor
public class PetPostServiceImpl implements PetPostService {
    private final PetPostRepository petPostRepository;
    // ...
}
```

---

## Phase 1: 기반 구축

모든 도메인에서 공통으로 사용하는 인프라 코드를 먼저 세팅한다.

### 1-1. 의존성 추가

**파일:** `build.gradle`

```groovy
// 추가할 의존성
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
implementation 'software.amazon.awssdk:s3'
```

### 1-2. BackendApplication 수정

**파일:** `BackendApplication.java`

- `@EnableScheduling` 추가 (스케줄��� 활성화)
- 기존 `@EnableJpaAuditing` 유지

### 1-3. 패키지 구조 세팅

기존 `domain/petPost` 패키지를 그대로 유지하고 확장.

```
com.jipsamoye.backend
├── global
│   ├── config/          // SecurityConfig, S3Config, CorsConfig
│   ├── entity/          // BaseEntity (이미 존재)
│   ├── code/            // ErrorCode
│   ├── response/        // ApiResponse, ErrorResponse, PageResponse
│   ├── exception/       // BusinessException, GlobalExceptionHandler
│   └── scheduler/       // PopularPostScheduler, GuestCleanupScheduler
│
├── domain
│   ├── auth/
│   │   ├── controller/  // AuthController
│   │   ├── service/     // AuthService, AuthServiceImpl, CustomOAuth2UserService
│   │   ├── handler/     // OAuth2SuccessHandler, OAuth2FailureHandler
│   │   └── dto/         // OAuth2UserInfo
│   │
│   ├── user/
│   │   ├── controller/  // UserController
│   │   ├── service/     // UserService, UserServiceImpl
│   │   ├── repository/  // UserRepository
│   │   ├── entity/      // User, Provider, Role
│   │   └── dto/         // UserResponse, UserUpdateRequest
│   │
│   ├── petPost/
│   │   ├── controller/  // PetPostController
│   │   ├── service/     // PetPostService, PetPostServiceImpl
│   │   ├── repository/  // PetPostRepository
│   │   ├── entity/      // PetPost (기존 엔티티 확장)
│   │   └── dto/         // PetPostCreateRequest, PetPostUpdateRequest, PetPostResponse, PetPostListResponse
│   │
│   ├── comment/
│   │   ├── controller/  // CommentController
│   │   ├── service/     // CommentService, CommentServiceImpl
│   │   ├── repository/  // CommentRepository
│   │   ├── entity/      // Comment
│   │   └── dto/         // CommentCreateRequest, CommentUpdateRequest, CommentResponse
│   │
│   ├── like/
│   │   ├── controller/  // LikeController
│   │   ├── service/     // LikeService, LikeServiceImpl
│   │   ├── repository/  // LikeRepository
│   │   └── entity/      // Like
│   │
│   ├── follow/
│   │   ├── controller/  // FollowController
│   │   ├── service/     // FollowService, FollowServiceImpl
│   │   ├── repository/  // FollowRepository
│   │   ├── entity/      // Follow
│   │   └── dto/         // FollowResponse
│   │
│   └── image/
│       ├── controller/  // ImageController
│       ├── service/     // ImageService, ImageServiceImpl
│       └── dto/         // PresignedUrlRequest, PresignedUrlResponse
│
├── HealthController.java (유지)
└── BackendApplication.java
```

### 1-4. 공통 응답 형식

**생성할 파일:**
- `global/response/ApiResponse.java` — 통일된 응답 래퍼
- `global/response/ErrorResponse.java` — 에러 응답
- `global/response/PageResponse.java` — 페이지네이션 응답 래퍼

```java
// 성공/실패 동일 형식
{ "status": 200, "code": "SUCCESS", "message": "...", "data": { ... } }
{ "status": 400, "code": "INVALID_FILE", "message": "...", "data": null }
```

### 1-5. 에러 처리

**생성할 파일:**
- `global/code/ErrorCode.java` — 에러 코드 Enum
- `global/exception/BusinessException.java` — 커스텀 예외
- `global/exception/GlobalExceptionHandler.java` — 전역 예외 핸들러

처리 대상: BusinessException, MethodArgumentNotValidException, MissingServletRequestParameterException, HttpMessageNotReadableException, Exception (catch-all)

### 1-6. Spring Security + 세션 설정

**생성할 파일:**
- `global/config/SecurityConfig.java`

설정 내용:
- CORS: allowedOrigins (`http://localhost:3000`), allowCredentials(true)
- CSRF 비활성화 (API 서버)
- **초기 개발 단계: 전체 permitAll** (인증 없이 Swagger로 테스트)
- Phase 2-8 (인증 구현) 시점에 인가 규칙 적용:
  - permitAll: `GET /api/posts/**`, `GET /api/users/**`, `GET /api/search`, `GET /api/posts/popular`, `/oauth2/**`, `/`
  - authenticated: 그 외 전부
  - 세션 쿠키: HttpOnly, Secure(운영), SameSite=None
  - OAuth2 로그인 설정 (SuccessHandler, FailureHandler)

### 1-7. Enum 타입 생성

**생성할 파일:**
- `domain/user/entity/Provider.java` — KAKAO, NAVER, GOOGLE, GUEST
- `domain/user/entity/Role.java` — USER, GUEST

### 1-8. S3 설정

**생성할 파일:**
- `global/config/S3Config.java` — S3Client Bean 등록

**application.yaml 추가 설정:**
```yaml
# application-local.yaml
cloud:
  aws:
    s3:
      bucket: ${AWS_S3_BUCKET}
    credentials:
      access-key: ${AWS_ACCESS_KEY}
      secret-key: ${AWS_SECRET_KEY}
    region:
      static: ap-northeast-2
```

---

## Phase 2: P0 핵심 기능

> **전략:** 핵심 도메인(게시글, 댓글, 좋아요)을 먼저 구현하고 Swagger로 바로 테스트.
> 인증(OAuth2, 둘러보기)은 나중에 얹는다. SecurityConfig는 초기에 전체 permitAll.

### 2-1. User 엔티티 + Repository (엔티티만)

User는 PetPost의 FK로 필요하므로 엔티티와 Repository만 먼저 생성. Controller/Service는 2-6에서.

**생성할 파일:**
- `domain/user/entity/User.java`
- `domain/user/repository/UserRepository.java`

User 필드: id, nickname, bio, profileImageUrl, email, provider, providerId, role

UserRepository 메서드:
- `findByNickname(String nickname)` → Optional
- `findByProviderAndProviderId(Provider, String)` → Optional
- `existsByNickname(String nickname)` → boolean

### 2-2. PetPost 엔티티 확장 + CRUD

**기존 PetPost 엔티티에 필드 추가:**
- 추가 필드: user(FK), imageUrls(JSON), likeCount

**수정할 파일:**
- `domain/petPost/entity/PetPost.java` — 필드 확장

**생성할 파일:**
- `domain/petPost/repository/PetPostRepository.java`
- `domain/petPost/controller/PetPostController.java`
- `domain/petPost/service/PetPostService.java` (인터페이스)
- `domain/petPost/service/PetPostServiceImpl.java` (구현체)
- `domain/petPost/dto/PetPostCreateRequest.java`
- `domain/petPost/dto/PetPostUpdateRequest.java`
- `domain/petPost/dto/PetPostResponse.java`
- `domain/petPost/dto/PetPostListResponse.java`

PetPostRepository 메서드:
- `findAllByOrderByCreatedAtDesc(Pageable)` → Page<PetPost>
- `findAllByUser(User, Pageable)` → Page<PetPost>
- `@Query("UPDATE PetPost p SET p.likeCount = p.likeCount + :value WHERE p.id = :id")` → likeCount 원자적 증감

API:
- `GET /api/posts?page=0&size=20` — 목록 (페이지네이션)
- `GET /api/posts/{id}` — 상세
- `POST /api/posts` — 작성 (제목 1~100자, 내용 1~5000자, 이미지 1~5장)
- `PATCH /api/posts/{id}` — 수정 (본인만)
- `DELETE /api/posts/{id}` — 삭제 (본인만, 연관 댓글·좋아요 cascade + S3 이미지 삭제)

> 인증 미구현 단계에서는 테스트용 유저를 DB에 직접 생성하여 테스트

### 2-3. S3 Presigned URL

**생성할 파일:**
- `domain/image/controller/ImageController.java`
- `domain/image/service/ImageService.java` (인터페이스)
- `domain/image/service/ImageServiceImpl.java` (구현체)
- `domain/image/dto/PresignedUrlRequest.java`
- `domain/image/dto/PresignedUrlResponse.java`

API:
- `POST /api/images/presigned-url` → 파일명, 파일타입 받아서 { presignedUrl, imageUrl } 반환

제약: JPG/PNG/WEBP만, 10MB 제한, 만료 10분
버킷 경로: `profiles/{userId}/{uuid}.{ext}`, `posts/{userId}/{uuid}.{ext}`

### 2-4. 댓글 CRUD

**생성할 파일:**
- `domain/comment/entity/Comment.java`
- `domain/comment/repository/CommentRepository.java`
- `domain/comment/controller/CommentController.java`
- `domain/comment/service/CommentService.java` (인터페이스)
- `domain/comment/service/CommentServiceImpl.java` (구현체)
- `domain/comment/dto/CommentCreateRequest.java`
- `domain/comment/dto/CommentUpdateRequest.java`
- `domain/comment/dto/CommentResponse.java`

CommentRepository 메서드:
- `findAllByPetPostOrderByCreatedAtDesc(PetPost, Pageable)` → Page<Comment>
- `deleteAllByPetPost(PetPost)` → cascade 삭제용

API:
- `GET /api/posts/{postId}/comments?page=0&size=20` — 댓글 목록
- `POST /api/posts/{postId}/comments` — 댓글 작성 (1~500자)
- `PATCH /api/comments/{id}` — 수정 (본인만)
- `DELETE /api/comments/{id}` — 삭제 (본인만)

### 2-5. 좋아요

**생성할 파일:**
- `domain/like/entity/Like.java`
- `domain/like/repository/LikeRepository.java`
- `domain/like/controller/LikeController.java`
- `domain/like/service/LikeService.java` (인터페이스)
- `domain/like/service/LikeServiceImpl.java` (구현체)

LikeRepository 메서드:
- `findByPetPostAndUser(PetPost, User)` → Optional
- `existsByPetPostAndUser(PetPost, User)` → boolean
- `deleteAllByPetPost(PetPost)` → cascade 삭제용

API:
- `POST /api/posts/{postId}/like` — 좋아요 토글

로직:
- 이미 좋아요 → 삭제 + likeCount - 1
- 좋아요 안 했으면 → 생성 + likeCount + 1
- likeCount: PetPostRepository의 `@Query`로 원자적 증감

### 2-6. 유저 프로필

**생성할 파일:**
- `domain/user/controller/UserController.java`
- `domain/user/service/UserService.java` (인터페이스)
- `domain/user/service/UserServiceImpl.java` (구현체)
- `domain/user/dto/UserResponse.java`
- `domain/user/dto/UserUpdateRequest.java`

API:
- `GET /api/users/{nickname}` — 프로필 조회 (게시글 수, 팔로워/팔로잉 수 포함)
- `PATCH /api/users/me` — 프로필 수정 (닉네임, 소개글, 프로필 이미지)
- `GET /api/users/{nickname}/posts?page=0&size=20` — 유저 게시글 목록

Validation:
- 닉네임: `^[가-힣a-zA-Z0-9]{2,10}$`, 중복 불가
- 소개글: 최대 200자

> 팔로워/팔로잉 목록 API는 Phase 3 (팔로우) 구현 시 UserController에 추가

### 2-7. OAuth2 소셜 로그인

**생성할 파일:**
- `domain/auth/service/CustomOAuth2UserService.java` — OAuth2UserService 구현
- `domain/auth/handler/OAuth2SuccessHandler.java` — 성공 → 프론트 메인 리다이렉트
- `domain/auth/handler/OAuth2FailureHandler.java` — 실패 → 프론트 로그인 리다이렉트
- `domain/auth/dto/OAuth2UserInfo.java` — 소셜별 유저 정보 추출

**application-local.yaml 추가:**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao: { client-id, client-secret, redirect-uri, ... }
          naver: { client-id, client-secret, redirect-uri, ... }
          google: { client-id, client-secret, redirect-uri, ... }
```

흐름:
1. `/oauth2/authorization/{provider}` → 소셜 로그인
2. CustomOAuth2UserService에서 유저 조회, 없으면 자동 생성 (랜덤 닉네임 부여)
3. 세션 생성 → OAuth2SuccessHandler → 프론트 메인으로 리다이렉트

### 2-8. 둘러보기 (임시 계정) + 인증 공통 + SecurityConfig 인가 규칙 적용

**생성할 파일:**
- `domain/auth/controller/AuthController.java`
- `domain/auth/service/AuthService.java` (인터페이스)
- `domain/auth/service/AuthServiceImpl.java` (구현체)

API:
- `POST /api/auth/guest` → UUID 닉네임 + GUEST role 유저 생성 → 세션 발급
- `POST /api/auth/logout` → 세션 무효화
- `DELETE /api/auth/withdraw` → 유저 + 관련 데이터 + S3 이미지 전체 삭제
- `GET /api/auth/me` → 현재 로그인 유저 정보 반환 (프론트에서 로그인 상태 확인용)

**SecurityConfig 업데이트:**
- 전체 permitAll → 인가 규칙 적용으로 전환
- 세션 쿠키 설정 (HttpOnly, Secure, SameSite)

---

## Phase 3: P1 기능

### 3-1. 팔로우/언팔로우

**생성할 파일:**
- `domain/follow/entity/Follow.java`
- `domain/follow/repository/FollowRepository.java`
- `domain/follow/controller/FollowController.java`
- `domain/follow/service/FollowService.java` (인터페이스)
- `domain/follow/service/FollowServiceImpl.java` (구현체)
- `domain/follow/dto/FollowUserResponse.java`

FollowRepository 메서드:
- `findByFollowerAndFollowing(User, User)` → Optional
- `findAllByFollowing(User, Pageable)` → Page<Follow> (팔로워 목록)
- `findAllByFollower(User, Pageable)` → Page<Follow> (팔로잉 목록)
- `countByFollowing(User)` → long (팔로워 수)
- `countByFollower(User)` → long (팔로잉 수)

**UserController에 추가할 API:**
- `GET /api/users/{nickname}/followers?page=0&size=20` — 팔로워 목록
- `GET /api/users/{nickname}/following?page=0&size=20` — 팔로잉 목록

API:
- `POST /api/users/{nickname}/follow` — 팔로우 토글

제약: 자기 자신 팔로우 방지

### 3-2. 오늘의 멍냥 (인기 게시글 스케줄러)

**생성할 파일:**
- `global/scheduler/PopularPostScheduler.java`

PetPostRepository에 메서드 추가:
- `@Query` — 최근 24시간 내 좋아요 수 상위 10개 조회

로직:
- `@Scheduled(fixedRate = 3600000)` — 1시간마다 실행
- 결과를 `List<PetPostListResponse>`로 메모리 캐싱
- 애플리케이션 시작 ��에도 1회 실행 (`@PostConstruct` 또는 `initialDelay = 0`)

**PetPostController에 추가할 API:**
- `GET /api/posts/popular` — 캐싱된 인기 게시글 반환

### 3-3. 검색

PetPostRepository에 메서드 추가:
- `findByTitleContaining(String keyword, Pageable pageable)` → Page<PetPost>

**PetPostController에 추가할 API:**
- `GET /api/search?q={keyword}&page=0&size=20` — 제목 기반 검색

### 3-4. 둘러보기 유저 정리 스케줄러

**생성할 파일:**
- `global/scheduler/GuestCleanupScheduler.java`

로직:
- `@Scheduled(cron = "0 0 3 * * *")` — 매일 새벽 3���
- role=GUEST && createdAt < 24시간 전인 유저 조회
- 삭제 순서: likes → comments → posts(+S3 이미지) → follows → users(+S3 프로필 이미지)
- S3 삭제는 ImageService 재사용

---

## Phase 4: 테스트 + 마무리

### 4-1. 통합 테스트 작성

**도구:** `@SpringBootTest` + `@Transactional` + 로컬 MySQL

| 도메인 | 테스트 케이스 |
|--------|-------------|
| Auth | 둘러보기 계정 생성, 로그아웃, 회원 탈퇴 시 데이터 삭제 |
| User | 프로필 조회, 수정, 닉네임 중복 검증, 닉네임 유효성 검증 |
| Post | CRUD, 이미지 URL 리스트 저장/조회, 페이지네이션, 본인만 수���/삭제 |
| Comment | CRUD, 본인만 수정/삭제 검증 |
| Like | 좋아요 토글, 중복 방지, likeCount 동기화 |
| Follow | 팔로우 토글, 자기 자신 팔로우 방지, 팔로워/팔로잉 목록 |

### 4-2. Swagger 문서 정리

- 각 Controller에 `@Operation`, `@ApiResponse` 어노테이션
- DTO에 `@Schema` 설명 추가
- Swagger UI에서 전체 API 동작 확인

### 4-3. 배포 검증

**application-prod.yaml 추가 설정:**
- OAuth2 client-id, client-secret (카카오, 네이버, 구���)
- AWS S3 credentials, bucket
- 세션 쿠키 Secure=true
- CORS allowedOrigins 운영 도메인

배포 → 전체 기능 동작 확인

---

## 구현 순서 요약

```
Phase 1 (기반)
  [x] 1-1 의존성 추가
  [x] 1-2 BackendApplication 수정 (@EnableScheduling)
  [x] 1-3 패키지 구조 세팅
  [x] 1-4 공통 응답 형식 (ApiResponse 통일)
  [x] 1-5 에러 처리 (ErrorCode, BusinessException, GlobalExceptionHandler)
  [x] 1-6 Security + 세션 (초기 permitAll)
  [x] 1-7 Enum 타입 (Provider, Role)
  [x] 1-8 S3 설정 (@Profile("s3")로 비활성화, 추후 활성화)

Phase 2 (P0 핵심) — 핵심 도메인 먼저, 인증은 나중에
  [x] 2-1 User 엔티티 + Repository (엔티티만, Controller/Service 없이)
  [x] 2-2 PetPost 확장 + CRUD (Swagger로 바로 테스트)
  [ ] 2-3 S3 Presigned URL → S3 버킷 미생성으로 보류, 추후 진행
  [x] 2-4 댓글 CRUD (CommentService/Impl)
  [x] 2-5 좋아요 (LikeService/Impl)
  [x] 2-6 유저 프로필 (UserController, UserService/Impl)
  [ ] 2-7 OAuth2 소셜 로그인 → 소셜 검수 필요, 추후 진행
  [x] 2-8 둘러보기 + 인증 공통 (SecurityConfig 인가 규칙은 OAuth2 구현 시 적용)

Phase 2 완료 후 → develop을 main에 머지하여 배포

Phase 3 (P1)
  [ ] 3-1 팔로우 (FollowService/Impl) + UserController에 팔로워/팔로잉 API 추가
  [ ] 3-2 오늘의 멍냥 스케줄러
  [ ] 3-3 검색
  [ ] 3-4 게스트 정리 스케줄러

Phase 4 (마무리)
  [ ] 4-1 통합 테스트
  [ ] 4-2 Swagger 정리
  [ ] 4-3 배포 검증
```
