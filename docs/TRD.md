# 집사모여 — TRD (Technical Requirements Document)

> **버전:** v1.0 (MVP)
> **작성일:** 2026-04-10
> **기반 문서:** [PRD](./PRD.md)

---

## 1. 시스템 아키텍처

```
[Client]                  [EC2]                         [Storage]
Next.js  ──── HTTPS ──── Nginx ──── Proxy ──── Spring Boot ──── MySQL
(Vercel)                  (리버스 프록시)        (Docker)         (Docker)
    │                                               │
    └──── Presigned URL ──── S3 (이미지 직접 업로드)  │
                                                    └── S3 URL 발급
```

- Frontend → Vercel 배포, Nginx를 통해 Backend API 호출
- Nginx → 리버스 프록시, 클라이언트 요청을 Spring Boot로 전달
- Backend → EC2 Docker 컨테이너
- DB → MySQL 8.0 (EC2 Docker Compose)
- 이미지 → 프론트가 Presigned URL로 S3에 직접 업로드 (서버 경유 없음)
- CI/CD → GitHub Actions (구성 완료)

---

## 2. 기술 스택 상세

| 구분 | 기술 | 비고 |
|------|------|------|
| Language | Java 17 | |
| Framework | Spring Boot 3.5.13 | |
| ORM | Spring Data JPA | Hibernate |
| Auth | Spring Security + OAuth2 Client | 세션 기반 |
| Validation | Spring Boot Starter Validation | |
| API 문서 | springdoc-openapi (Swagger UI) | 이미 적용됨 |
| Image | AWS SDK v2 (S3 Presigned URL) | |
| DB | MySQL 8.0 | EC2 Docker |
| Build | Gradle | |
| Infra | EC2, Docker, Nginx, GitHub Actions | 구성 완료 |
| Frontend | Next.js | Vercel 배포 |

### 2.1 추가 필요 의존성

```groovy
// Spring Security + OAuth2
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'

// AWS S3
implementation 'software.amazon.awssdk:s3'

// JSON 타입 (이미지 URL 리스트 저장) — Hibernate 6.3+ 네이티브 지원, 별도 라이브러리 불필요
```

---

## 3. 프로젝트 패키지 구조

```
com.jipsamoye.backend
├── global
│   ├── config          // SecurityConfig, S3Config, SwaggerConfig 등
│   ├── entity          // BaseEntity
│   ├── code            // ErrorCode (Enum)
│   ├── response        // ApiResponse, ErrorResponse
│   ├── exception       // BusinessException, GlobalExceptionHandler
│   └── scheduler       // PopularPostScheduler, GuestCleanupScheduler
│
├── domain
│   ├── auth
│   │   ├── controller  // AuthController
│   │   ├── service     // AuthService, OAuth2UserService
│   │   └── dto         // LoginResponse 등
│   │
│   ├── user
│   │   ├── controller  // UserController
│   │   ├── service     // UserService
│   │   ├── repository  // UserRepository
│   │   ├── entity      // User
│   │   └── dto         // UserResponse, UserUpdateRequest 등
│   │
│   ├── petPost
│   │   ├── controller  // PetPostController
│   │   ├── service     // PetPostService, PetPostServiceImpl
│   │   ├── repository  // PetPostRepository
│   │   ├── entity      // PetPost (기존 엔티티 확장)
│   │   └── dto         // PetPostRequest, PetPostResponse 등
│   │
│   ├── comment
│   │   ├── controller  // CommentController
│   │   ├── service     // CommentService
│   │   ├── repository  // CommentRepository
│   │   ├── entity      // Comment
│   │   └── dto         // CommentRequest, CommentResponse 등
│   │
│   ├── like
│   │   ├── controller  // LikeController
│   │   ├── service     // LikeService
│   │   ├── repository  // LikeRepository
│   │   └── entity      // Like
│   │
│   ├── follow
│   │   ├── controller  // FollowController
│   │   ├── service     // FollowService
│   │   ├── repository  // FollowRepository
│   │   └── entity      // Follow
│   │
│   └── image
│       ├── controller  // ImageController
│       └── service     // S3PresignedUrlService
│
└── BackendApplication.java
```

---

## 4. 인증/인가

### 4.1 세션 기반 인증

- Spring Security + HttpSession
- 세션 저장: 서버 메모리 (기본, 단일 서버)
- 세션 만료: 30분 (기본값)

### 4.2 CORS + 세션 쿠키

**CORS 설정 (SecurityConfig)**
- allowedOrigins: 프론트엔드 도메인 (로컬: `http://localhost:3000`)
- allowedMethods: GET, POST, PATCH, DELETE
- allowCredentials: `true` (세션 쿠키 전달 필수)

**세션 쿠키 설정**
- `HttpOnly`: true (XSS 방지)
- `Secure`: true (HTTPS 전용, 운영 환경)
- `SameSite`: None (cross-origin 쿠키 전달)

**도메인 관련 (배포 시)**
- 프론트와 백엔드가 다른 도메인이면 세션 쿠키 전달이 제한됨
- 배포 시 커스텀 도메인 필수: 프론트 `jipsamoye.com` / 백엔드 `api.jipsamoye.com`
- 로컬 개발에서는 localhost로 동일 origin이므로 문제없음

### 4.3 OAuth2 소셜 로그인

| 제공자 | 흐름 |
|--------|------|
| 카카오 | OAuth2 Client → 카카오 인증 → 콜백 → 세션 생성 |
| 네이버 | OAuth2 Client → 네이버 인증 → 콜백 → 세션 생성 |
| 구글 | OAuth2 Client → 구글 인증 → 콜백 → 세션 생성 |

**처리 흐름:**
1. 프론트에서 `/oauth2/authorization/{provider}` 로 리다이렉트
2. 소셜 로그인 완료 → Spring Security OAuth2 Client가 콜백 처리
3. CustomOAuth2UserService에서 유저 조회/생성
4. 세션 생성 → 프론트로 리다이렉트
5. 이후 요청은 세션 쿠키로 인증

**OAuth2 리다이렉트 설정:**
- 로그인 성공 → 프론트 메인 페이지 리다이렉트 (OAuth2SuccessHandler)
- 로그인 실패 → 프론트 로그인 페이지 리다이렉트 (OAuth2FailureHandler)

### 4.4 둘러보기 유저

- `POST /api/auth/guest` → UUID 기반 임시 유저 생성 → 세션 발급
- role: `GUEST`
- 스케줄러로 24시간 경과한 GUEST 계정 + 관련 데이터 일괄 삭제

### 4.5 인가 규칙

| 리소스 | 비로그인 | GUEST | USER |
|--------|----------|-------|------|
| 게시글 조회 | O | O | O |
| 게시글 작성/수정/삭제 | X | O (본인) | O (본인) |
| 좋아요 | X | O | O |
| 댓글 작성/수정/삭제 | X | O (본인) | O (본인) |
| 팔로우 | X | O | O |
| 프로필 수정 | X | O (본인) | O (본인) |

---

## 5. 엔티티 설계

### 5.1 공통

```java
// BaseEntity — 이미 구현됨
@MappedSuperclass
public abstract class BaseEntity {
    private LocalDateTime createdAt;  // @CreatedDate
    private LocalDateTime updatedAt;  // @LastModifiedDate
}
```

### 5.2 User

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String nickname;        // 2~10자, 한글/영문/숫자만

    @Column(columnDefinition = "TEXT")
    private String bio;             // 소개글 (선택)

    private String profileImageUrl; // null이면 프론트에서 기본 이미지

    @Column(nullable = false)
    private String email;           // 소셜 제공

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;      // KAKAO, NAVER, GOOGLE, GUEST

    @Column(nullable = false)
    private String providerId;      // 소셜 고유 ID (GUEST는 UUID)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;              // USER, GUEST
}
```

### 5.3 PetPost

```java
@Entity
@Table(name = "pet_post")
public class PetPost extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> imageUrls; // S3 URL 리스트 (1~5장), Hibernate 6.3+ 네이티브 JSON 매핑

    @Column(nullable = false)
    private int likeCount = 0;      // 비정규화 (좋아요 수)
    // 동시성: UPDATE pet_post SET like_count = like_count ± 1 WHERE id = ? (@Query로 원자적 증감)
}
```

### 5.4 Comment

```java
@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "pet_post_id", nullable = false)
    private PetPost petPost;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
```

### 5.5 Like

```java
@Entity
@Table(name = "likes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"pet_post_id", "user_id"})
})
public class Like extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "pet_post_id", nullable = false)
    private PetPost petPost;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

### 5.6 Follow

```java
@Entity
@Table(name = "follows", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"follower_id", "following_id"})
})
public class Follow extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;
}
```

### 5.7 ERD

```
users (1) ──── (N) pet_posts
users (1) ──── (N) comments
users (1) ──── (N) likes
users (N) ──── (N) follows (self-referencing)
pet_posts (1) ──── (N) comments
pet_posts (1) ──── (N) likes
```

---

## 6. 삭제 전략

- **Hard delete** — DB에서 완전 삭제
- 게시글 삭제 시 → 연관 댓글, 좋아요 cascade 삭제 + S3 이미지 삭제
- 회원 탈퇴 시 → 유저의 게시글, 댓글, 좋아��, 팔로우 전체 삭제 + S3 이미지 전체 삭제
- 게스트 정리 시 → 위와 동일 (스케줄러)

---

## 7. API 설계

### 7.1 공통 응답 형식

모든 API는 동일한 형식으로 응답한다.

```json
{
  "status": 200,
  "code": "SUCCESS",
  "message": "요청 성공",
  "data": { ... }
}
```

```json
{
  "status": 400,
  "code": "INVALID_FILE",
  "message": "올바르지 않은 파일입니다.",
  "data": null
}
```

### 7.2 페이지네이션 (Offset-based)

목록 조회 API는 Offset 방식 페이지네이션을 사용한다. (추후 데이터 증가 시 Cursor 방식으로 전환 검토)

**요청:** `?page=0&size=20`
**응답:** Spring Data의 `Page` 객체를 활용
```json
{
  "status": 200,
  "code": "SUCCESS",
  "message": "요청 성공",
  "data": {
    "content": [ ... ],
    "totalPages": 5,
    "totalElements": 100,
    "currentPage": 0,
    "size": 20,
    "hasNext": true
  }
}
```

**적용 대상:** 게시글 목록, 댓글 목록, 유저 게시글 목록, 팔로워/팔로잉 목록, 검색 결과

### 7.3 인증 API

```
POST   /api/auth/guest              둘러보기 임시 계정 생성 + 세션 발급
POST   /api/auth/logout             로그아웃 (세션 무효화)
DELETE /api/auth/withdraw            회원 탈퇴
GET    /oauth2/authorization/{provider}  소셜 로그인 시작 (Spring Security 제공)
```

### 7.4 유저 API

```
GET    /api/users/{nickname}           프로필 조회
PATCH  /api/users/me                   프로필 수정 (닉네임, 소개글, 프로필 이미지)
GET    /api/users/{nickname}/posts     유저 게시글 목록
GET    /api/users/{nickname}/followers 팔로워 목록
GET    /api/users/{nickname}/following 팔로잉 목록
```

### 7.5 게시글 API

```
GET    /api/posts                 게시글 목록 (페이지네이션)
GET    /api/posts/{id}            게시글 상세
POST   /api/posts                 게시글 작성
PATCH  /api/posts/{id}            게시글 수정
DELETE /api/posts/{id}            게시글 삭제
GET    /api/posts/popular         오늘의 멍냥 (캐싱된 인기 게시글)
```

### 7.6 댓글 API

```
GET    /api/posts/{postId}/comments    댓글 목록
POST   /api/posts/{postId}/comments    댓글 작성
PATCH  /api/comments/{id}              댓글 수정
DELETE /api/comments/{id}              댓글 삭제
```

### 7.7 좋아요 API

```
POST   /api/posts/{postId}/like        좋아요 토글
```

### 7.8 팔로우 API

```
POST   /api/users/{nickname}/follow    팔로우 토글
```

### 7.9 이미지 API

```
POST   /api/images/presigned-url       S3 Presigned URL 발급
```

### 7.10 검색 API

```
GET    /api/search?q={keyword}         제목 기반 검색
```

---

## 8. 에러 처리

### 8.1 ErrorCode (Enum)

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    BAD_REQUEST(400, "BAD_REQUEST", "잘못된 요청입니다."),
    INVALID_INPUT(400, "INVALID_INPUT", "입력값이 유효하지 않습니다."),
    INVALID_FILE(400, "INVALID_FILE", "올바르지 않은 파일입니다."),
    MISSING_PARAMETER(400, "MISSING_PARAMETER", "필수 파라미터가 누락되었습니다."),

    // 401
    UNAUTHORIZED(401, "UNAUTHORIZED", "로그인이 필요합니다."),

    // 403
    FORBIDDEN(403, "FORBIDDEN", "권한이 없습니다."),

    // 404
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "유저를 찾을 수 없습니다."),
    POST_NOT_FOUND(404, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(404, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."),

    // 409
    DUPLICATE_NICKNAME(409, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."),
    DUPLICATE_LIKE(409, "DUPLICATE_LIKE", "이미 좋아요한 게시글입니다."),

    // 500
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),
    S3_UPLOAD_ERROR(500, "S3_UPLOAD_ERROR", "이미지 업로드에 실패했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
```

**참고 코드 대비 개선점:**
- `divisionCode` (G001, B998) → 읽기 쉬운 문자열 코드로 변경
- `SuccessCode` 제거 → 성공은 HTTP 상태코드로 충분
- INSERT_ERROR(200) 같은 잘못된 상태코드 제거
- 도메인별 에러코드 (USER_NOT_FOUND, POST_NOT_FOUND) 추가
- NullPointerException 별도 핸들링 제거 → 기본 Exception 핸들러로 위임

### 8.2 BusinessException

```java
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

### 8.3 GlobalExceptionHandler

처리 대상:
- `BusinessException` → ErrorCode 기반 응답
- `MethodArgumentNotValidException` → Validation 에러 상세 응답
- `MissingServletRequestParameterException` → 파라미터 누락
- `HttpMessageNotReadableException` → 요청 본문 파싱 실패
- `Exception` → 500 기본 에러 (catch-all)

---

## 9. S3 이미지 업로드

### 9.1 Presigned URL 흐름

```
1. 프론트 → POST /api/images/presigned-url (파일명, 파일타입)
2. 백엔드 → S3 Presigned URL 생성 (만료: 10분)
3. 백엔드 → 프론트에 { presignedUrl, imageUrl } 반환
4. 프론트 → Presigned URL로 S3에 직접 PUT 업로드
5. 프론트 → 게시글 작성 시 imageUrl을 포함하여 전송
```

### 9.2 제약 사항

- 허용 형식: JPG, PNG, WEBP
- 최대 파일 크기: 10MB/장
- 게시글당 최대 5장
- 프로필 이미지: 1장
- Presigned URL 만료: 10분

### 9.3 S3 버킷 구조

```
jipsamoye-images/
├── profiles/{userId}/{uuid}.{ext}
└── posts/{userId}/{uuid}.{ext}
```

---

## 10. 스케줄러

### 10.1 오늘의 멍냥 (인기 게시글)

- **주기:** 1시간 (`@Scheduled(fixedRate = 3600000)`)
- **로직:** 최근 24시간 내 좋아요 수 상위 10개 조회
- **저장:** 메모리 캐싱 (static 변수 또는 ConcurrentHashMap)
- **API:** `GET /api/posts/popular` → 캐싱된 결과 반환

### 10.2 둘러보기 유저 정리

- **주기:** 매일 새벽 3시 (`@Scheduled(cron = "0 0 3 * * *")`)
- **로직:** `role = GUEST` && `createdAt < 24시간 전`인 유저 + 관련 데이터 일괄 삭제
- **삭제 순서:** likes → comments → posts (+ S3 이미지) → follows → users (+ S3 프로필 이미지)

---

## 11. 유효성 검증

### 11.1 닉네임

- 2~10자
- 한글, 영문, 숫자만 허용 (특수문자, 공백 불가)
- 정규식: `^[가-힣a-zA-Z0-9]{2,10}$`
- 중복 불가

### 11.2 게시글

- 제목: 1~100자
- 내용: 1~5000자
- 이미지: 최소 1장, 최대 5장

### 11.3 댓글

- 내용: 1~500자

### 11.4 소개글

- 최대 200자

---

## 12. 테스트 전략

### 12.1 통합 테스트

- **범위:** Service 레이어 + Repository (실제 DB 연동)
- **DB:** 테스트용 MySQL (Testcontainers 또는 로컬 MySQL)
- **도구:** `@SpringBootTest` + `@Transactional`

### 12.2 테스트 대상 (도메인별)

| 도메인 | 주요 테스트 케이스 |
|--------|-------------------|
| Auth | 소셜 로그인 후 유저 생성, 둘러보기 계정 생성, 로그아웃 |
| User | 프로필 조회, 수정, 닉네임 중복 검증, 회원 탈퇴 시 데이터 삭제 |
| PetPost | CRUD, 이미지 URL 리스트 저장/조회, 페이지네이션 |
| Comment | CRUD, 본인 댓글만 수정/삭제 검증 |
| Like | 좋아요 토글, 중복 방지, likeCount 동기화 |
| Follow | 팔로우/언팔로우 토글, 자기 자신 팔로우 방지 |

---

## 13. 개발 환경

### 13.1 로컬 개발

- DB: 로컬 MySQL 직접 실행
- application-local.yml 프로필 분리
- S3: 개발용 버킷 별도 사용 (또는 LocalStack)

### 13.2 운영 배포

- DB: EC2 Docker Compose (MySQL 8.0) — 구성 완료
- App: EC2 Docker — 구성 완료
- Nginx: 리버스 프록시 — 구성 완료
- CI/CD: GitHub Actions — 구성 완료
- Frontend: Vercel 배포

### 13.3 환경변수 (application.yml 프로필 분리)

```
application.yml          # 공통 설정
application-local.yml    # 로컬 개발 (로컬 MySQL)
application-prod.yml     # 운영 (EC2 MySQL, S3)
```

주요 환경변수:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `OAUTH_KAKAO_CLIENT_ID`, `OAUTH_KAKAO_CLIENT_SECRET`
- `OAUTH_NAVER_CLIENT_ID`, `OAUTH_NAVER_CLIENT_SECRET`
- `OAUTH_GOOGLE_CLIENT_ID`, `OAUTH_GOOGLE_CLIENT_SECRET`
- `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_S3_BUCKET`

---

## 14. 구현 순서

```
Phase 1: 기반 구축
  ├── 프로젝트 구조 세팅 (패키지, 공통 응답, 에러 처리)
  ├── Spring Security + 세션 설정
  └── S3 Presigned URL 설정

Phase 2: P0 핵심 기능
  ├── OAuth2 소셜 로그인 (카카오, 네이버, 구글)
  ├── 둘러보기 (임시 계정)
  ├── 유저 프로필 (조회, 수정)
  ├── 게시글 CRUD + 이미지 업로드
  ├── 댓글 CRUD
  └── 좋아요

Phase 3: P1 기능
  ├── 팔로우/언팔로우
  ├── 오늘의 멍냥 (스케줄러)
  ├── 검색
  └── 둘러보기 유저 정리 스케줄러

Phase 4: 테스트 + 마무리
  ├── 통합 테스트 작성
  ├── Swagger 문서 정리
  └── 배포 검증
```
