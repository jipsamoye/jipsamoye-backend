# 집사모여 백엔드 아키텍처

## 시스템 아키텍처

```
[클라이언트]
Next.js (Vercel)
      │
      ▼
[인프라]
Nginx (리버스 프록시)
      │
      ▼
Spring Boot (Docker)
      │
      ├──▶ MySQL 8.0 (Docker)
      │
      ├──▶ AWS S3 (Presigned URL 이미지 업로드)
      │         │
      │         └──▶ Cloudflare CDN (이미지 서빙)
      │
      └──▶ Prometheus + Grafana (메트릭 수집 · 모니터링)
```

이미지 업로드 흐름:
1. 클라이언트가 서버에 Presigned URL 요청
2. 서버가 S3 Presigned URL 발급 후 반환
3. 클라이언트가 S3에 직접 업로드
4. 이미지 조회 시 Cloudflare CDN URL로 서빙

---

## 기술 스택

| 분류 | 기술 | 버전 |
|------|------|------|
| 언어 | Java | 17 |
| 프레임워크 | Spring Boot | 3.5.13 |
| ORM | Spring Data JPA (Hibernate) | - |
| 인증 | Spring Security + OAuth2 (세션 기반) | - |
| API 문서 | springdoc-openapi (Swagger UI) | - |
| 스토리지 | AWS S3 + Cloudflare CDN | - |
| 데이터베이스 | MySQL | 8.0 |
| 모니터링 | Prometheus + Grafana | - |
| 컨테이너 | Docker | - |
| 웹 서버 | Nginx | - |

---

## 도메인 맵

| 도메인 | 역할 | 주요 엔티티 |
|--------|------|------------|
| `auth` | 소셜 로그인, 세션 관리, 토큰 처리 | - |
| `user` | 회원 정보 조회 · 수정, 프로필 | `User` |
| `petPost` | 반려동물 게시글 CRUD, 피드 | `PetPost` |
| `comment` | 게시글 댓글 CRUD | `Comment` |
| `like` | 게시글 좋아요 · 취소 | `Like` |
| `follow` | 팔로우 · 언팔로우, 팔로워/팔로잉 목록 | `Follow` |
| `image` | S3 Presigned URL 발급, CDN URL 생성 | - |
| `notification` | 알림 생성 · 조회 · 읽음 처리 | `Notification` |
| `chat` | 채팅방 생성 · 참여 · 메시지 | `ChatRoom`, `ChatMessage` |
| `dm` | 1:1 다이렉트 메시지 | `DmRoom`, `DmMessage` |
| `board` | 자유게시판 CRUD (일반/질문 카테고리) | `Board` |
| `boardComment` | 자유게시판 댓글 CRUD | `BoardComment` |
| `boardLike` | 자유게시판 좋아요 토글 | `BoardLike` |

---

## 레이어 구조

```
Controller → Service → Repository → Entity
```

- 의존 방향은 위에서 아래(→)로만 허용
- 역방향 참조 금지 (예: Repository가 Service를 참조하거나, Entity가 Repository를 참조하는 것은 금지)
- Controller는 Service 인터페이스에만 의존, 구현체(Impl)에 직접 의존 금지

---

## 패키지 구조

```
src/main/java/com/jipsamoye/
├── domain/
│   └── {도메인}/                  # auth, user, petPost, comment, like, follow, image, notification, chat, dm
│       ├── controller/
│       ├── service/
│       │   ├── {도메인}Service.java          # 인터페이스
│       │   └── {도메인}ServiceImpl.java      # 구현체
│       ├── repository/
│       ├── entity/
│       ├── dto/
│       │   ├── request/
│       │   └── response/
│       └── event/                 # 도메인 이벤트 (필요 시)
│
└── global/
    ├── config/                    # Spring 설정 (Security, JPA, S3 등)
    ├── entity/                    # 공통 BaseEntity (createdAt, updatedAt, deletedAt)
    ├── code/                      # 에러 코드 enum
    ├── response/                  # ApiResponse, PageResponse 래퍼
    ├── exception/                 # GlobalExceptionHandler, 커스텀 예외
    ├── scheduler/                 # 스케줄러
    ├── logging/                   # Discord 에러 알림 등 로깅
    ├── dummy/                     # 개발용 더미 데이터 생성 (운영 배포 전 제거)
    └── util/                      # 유틸리티
```

---

## global 패키지 규칙

- `global` → `domain` 참조 **금지**
- `domain` → `global` 참조 **허용**
- global 패키지는 어떤 도메인에도 의존하지 않는 순수 공통 레이어

---

## 알려진 도메인 간 의존성 (기술 부채)

현재 일부 서비스가 다른 도메인의 Repository를 직접 참조하고 있어 레이어 경계가 약화된 상태.
추후 도메인 이벤트(ApplicationEvent) 또는 Facade 패턴으로 개선 필요.

### 직접 참조 현황

| 서비스 | 직접 참조하는 타 도메인 Repository |
|--------|----------------------------------|
| `AuthService` | `UserRepository`, `FollowRepository`, `PetPostRepository`, `LikeRepository`, `CommentRepository` |
| `UserService` | `FollowRepository`, `PetPostRepository` |
| `PetPostService` | `CommentRepository`, `LikeRepository` |

### 순환 참조 현황

| 순환 | 설명 |
|------|------|
| `user` ↔ `follow` | UserService가 FollowRepository 참조, FollowService가 UserRepository 참조 |
| `user` ↔ `petPost` | UserService가 PetPostRepository 참조, PetPostService가 UserRepository 참조 |

---

## 아키텍처 테스트 (ArchUnit)

위 레이어 규칙과 컨벤션은 ArchUnit 테스트로 기계적으로 강제된다.
테스트 위치: `src/test/java/com/jipsamoye/backend/global/architecture/ArchitectureTest.java`

**강제되는 규칙:**
1. Controller → Repository 직접 접근 금지
2. Service → Controller 역방향 참조 금지
3. Repository → Controller 역방향 참조 금지
4. Entity → Service/Controller 참조 금지
5. 도메인 간 순환 참조 금지 (현재 @Disabled — 기술 부채)
6. 엔티티 @Setter 금지
7. Controller 클래스 @RestController/@Controller 어노테이션 필수
8. DTO는 dto/request/ 또는 dto/response/에 위치

**규칙 위반 시:** `./gradlew test`에서 실패하며, 에러 메시지에 수정 방법이 포함되어 있다.
