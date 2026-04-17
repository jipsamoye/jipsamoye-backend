# 코드 컨벤션

집사모여 백엔드 프로젝트의 코드 작성 규칙입니다.

---

## 핵심 원칙 (에이전트 운영 철학)

판단이 애매할 때 이 원칙을 참고한다.

1. **기계적 강제 > 문서적 권유**: "~하는 것이 좋습니다"보다 ArchUnit/테스트로 강제한다. 문서로만 있는 규칙이 반복 위반되면 코드(테스트)로 승격한다.
2. **리포지터리 = 단일 진실 원천**: 설계 결정, 규칙, 컨텍스트는 리포지터리 내 파일에 존재해야 한다. Slack이나 머릿속에만 있는 지식은 에이전트에게 존재하지 않는다.
3. **경계에서 검증**: 외부 입력(@RequestBody, @RequestParam 등)은 반드시 @Valid + Bean Validation으로 검증한다. 내부 코드와 프레임워크 보장은 신뢰한다.
4. **작업 분해 우선**: 에이전트가 실패하면 "더 분발"이 아닌 "도구·가드레일·문서 중 뭐가 누락됐는지" 파악한다.
5. **점진적 공개**: CLAUDE.md(맵) → docs/(상세) → 코드 순으로 탐색한다. 한 번에 모든 컨텍스트를 로딩하지 않는다.

---

## 1. 엔티티 규칙

### @Setter 사용 금지

엔티티의 상태 변경은 의미 있는 메서드를 통해서만 수행합니다.

**BAD**
```java
@Setter
@Entity
public class User extends BaseEntity {
    private String nickname;
    private LocalDateTime deletedAt;
}

// 호출부
user.setNickname("새닉네임");
user.setDeletedAt(LocalDateTime.now());
```

**GOOD**
```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    private String nickname;
    private LocalDateTime deletedAt;

    public void updateProfile(String nickname, String bio) {
        if (nickname != null) this.nickname = nickname;
        if (bio != null) this.bio = bio;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
```

### Soft Delete

물리 삭제 대신 `deletedAt` 필드로 논리 삭제를 사용합니다.

- `deletedAt == null` → 활성 상태
- `deletedAt != null` → 삭제된 상태
- `isDeleted()` 헬퍼 메서드를 엔티티에 제공합니다.

```java
private LocalDateTime deletedAt;

public void softDelete() {
    this.deletedAt = LocalDateTime.now();
}

public boolean isDeleted() {
    return this.deletedAt != null;
}
```

### 정적 팩토리 메서드

public 생성자 대신 `create()` 정적 팩토리 메서드를 사용한다. 생성 시 초기 상태 설정과 비즈니스 검증을 포함할 수 있다.

```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA용 기본 생성자
public class Follow extends BaseEntity {

    public static Follow create(User follower, User following) {
        Follow follow = new Follow();
        follow.follower = follower;
        follow.following = following;
        return follow;
    }
}
```

### equals / hashCode

엔티티의 equals/hashCode는 **id 기반으로만** 비교한다. Lombok의 `@EqualsAndHashCode`는 모든 필드를 비교하므로 사용 금지.

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Follow follow)) return false;
    return id != null && id.equals(follow.getId());
}

@Override
public int hashCode() {
    return Objects.hash(id);
}
```

### BaseEntity 상속

모든 엔티티는 `global.entity.BaseEntity`를 상속합니다.  
`createdAt`, `updatedAt`은 JPA Auditing으로 자동 관리되므로 직접 선언하지 않습니다.

```java
public class PetPost extends BaseEntity {
    // createdAt, updatedAt은 BaseEntity에서 제공
}
```

---

## 2. 응답 형식

### ApiResponse 래퍼

모든 REST API 응답은 `global.response.ApiResponse<T>`로 감쌉니다.

```java
// Controller 반환 타입
public ResponseEntity<ApiResponse<UserResponse>> getProfile(...) {
    UserResponse response = userService.getProfile(nickname);
    return ResponseEntity.ok(ApiResponse.success(response));
}

// 생성(201)
return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(response));

// 메시지만 반환
return ResponseEntity.ok(ApiResponse.success("프로필 수정 성공"));
```

응답 구조:
```json
{
  "status": 200,
  "code": "SUCCESS",
  "message": "요청 성공",
  "data": { ... }
}
```

### 페이지네이션

페이지네이션 응답은 `global.response.PageResponse<T>`를 사용합니다.

```java
public ResponseEntity<ApiResponse<PageResponse<PetPostListResponse>>> getPosts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    PageResponse<PetPostListResponse> response = petPostService.getPosts(page, size);
    return ResponseEntity.ok(ApiResponse.success(response));
}
```

`PageResponse.from(Page<T> page)`로 Spring Data Page를 변환합니다.

---

## 3. Service 패턴

인터페이스와 구현체를 분리합니다.

```
domain/user/service/
  ├── UserService.java        // 인터페이스
  └── UserServiceImpl.java    // 구현체 (@Service)
```

```java
// UserService.java
public interface UserService {
    UserResponse getProfile(String nickname);
    UserResponse updateProfile(Long userId, UserUpdateRequest request);
}

// UserServiceImpl.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponse getProfile(String nickname) { ... }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UserUpdateRequest request) { ... }
}
```

- 기본값은 `@Transactional(readOnly = true)`로 설정하고, 쓰기 작업 메서드에만 `@Transactional`을 개별 적용합니다.

---

## 4. DTO 규칙

### Java Record 사용 (필수)

모든 DTO는 **Java Record**로 작성한다. Lombok 클래스(`@Getter`, `@NoArgsConstructor`)를 사용하지 않는다.

- 불변 보장 (필드가 자동으로 `final`)
- 보일러플레이트 제거 (getter, equals, hashCode, toString 자동 생성)
- Record의 접근자는 `getXxx()`가 아니라 `xxx()` 형태

### 패키지 위치

DTO는 반드시 `dto/request/` 또는 `dto/response/` 하위에 위치한다.
`dto/` 바로 아래에 두지 않는다.

```
domain/user/dto/
  ├── request/
  │   └── UserUpdateRequest.java
  └── response/
      └── UserResponse.java
```

### Request DTO

`@Valid` + Bean Validation 어노테이션을 record 파라미터에 직접 선언한다.

```java
@Schema(description = "게시글 작성 요청")
public record PetPostCreateRequest(
        @NotBlank(message = "제목을 입력해주세요.")
        @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
        @Schema(description = "게시글 제목") String title,

        @Size(max = 5000, message = "내용은 5000자 이하로 입력해주세요.")
        @Schema(description = "게시글 내용") String content,

        @NotEmpty(message = "이미지를 1장 이상 업로드해주세요.")
        @Size(max = 5, message = "이미지는 최대 5장까지 업로드할 수 있습니다.")
        @Schema(description = "이미지 URL 목록") List<String> imageUrls
) {}
```

### Response DTO

정적 팩토리 메서드 `from(Entity)`를 record 내부에 선언한다.

```java
@Schema(description = "댓글 응답")
public record CommentResponse(
        @Schema(description = "댓글 ID") Long id,
        @Schema(description = "작성자 닉네임") String nickname,
        @Schema(description = "댓글 내용") String content,
        @Schema(description = "작성일시") LocalDateTime createdAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getUser().getNickname(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
```

---

## 5. 에러 처리

비즈니스 예외는 `BusinessException(ErrorCode)`을 사용합니다.

```java
// ErrorCode 추가 위치: global.code.ErrorCode
public enum ErrorCode {
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "유저를 찾을 수 없습니다."),
    DUPLICATE_NICKNAME(409, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
    ...
}

// 사용
throw new BusinessException(ErrorCode.USER_NOT_FOUND);

// 커스텀 메시지
throw new BusinessException(ErrorCode.USER_NOT_FOUND, "탈퇴한 사용자입니다.");
```

새 에러 코드는 `global.code.ErrorCode`에 추가합니다. 도메인별로 그루핑하고 HTTP 상태코드 순으로 정렬합니다.

---

## 6. 주석

코드가 자명하면 주석을 달지 않습니다. 주석은 **"왜(why)"** 를 설명할 때만 사용합니다.

**BAD** — 코드 자체로 이미 명확한 경우
```java
// 유저를 조회한다
User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

**GOOD** — 비즈니스 판단 근거를 설명하는 경우
```java
// 닉네임이 변경된 경우에만 중복 검증 (본인 닉네임 재사용 허용)
if (!request.getNickname().equals(user.getNickname())) {
    validateNicknameDuplicate(request.getNickname());
}
```

---

## 7. 테스트 작성 규칙

### 메서드명과 구조

- `@DisplayName`은 한글로 작성한다.
- **Given-When-Then** 패턴을 따른다.
- `@Nested`로 메서드별 테스트를 그룹화한다.

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceImplTest {

    @Nested
    @DisplayName("updateProfile 메서드")
    class UpdateProfileTest {

        @Test
        @DisplayName("유효한 요청이면 프로필을 수정한다")
        void updateProfile_validRequest_updatesProfile() {
            // Given
            User user = UserFixture.defaultUser();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // When
            userService.updateProfile(1L, new UserUpdateRequest("새닉네임", null));

            // Then
            assertThat(user.getNickname()).isEqualTo("새닉네임");
        }
    }
}
```

### 테스트 픽스처 팩토리

테스트에서 반복되는 객체 생성은 `{도메인}Fixture` 클래스로 분리한다. 테스트 코드의 가독성과 재사용성이 높아진다.

```java
// src/test/java/.../domain/user/UserFixture.java
public class UserFixture {

    public static User defaultUser() {
        return User.create("test@example.com", "테스트유저", Provider.KAKAO, "kakao123");
    }

    public static UserUpdateRequest updateRequest() {
        return new UserUpdateRequest("새닉네임", "새소개글");
    }
}
```

---

## 8. 로깅 규칙

### 로그 레벨 기준

| 레벨 | 용도 | 예시 |
|------|------|------|
| ERROR | 시스템이 의도대로 동작하지 않음 (Discord 알림 트리거) | DB 연결 실패, 외부 API 장애 |
| WARN | 비즈니스 예외, 재시도 가능한 실패 | 중복 닉네임, 권한 없는 접근 시도 |
| INFO | 주요 비즈니스 이벤트 | 회원 가입, 게시글 생성, 배포 완료 |
| DEBUG | 개발 중 디버깅용 (운영에서는 OFF) | 쿼리 파라미터, 중간 계산 결과 |

### 금지 사항

- `System.out.println` 절대 금지. 반드시 `log.info/warn/error` 사용.
- 민감 정보(비밀번호, 토큰, 개인정보) 로깅 금지.

---

## 9. WebSocket Controller

`@MessageMapping`을 사용하는 WebSocket Controller는 `void` 반환을 허용합니다.  
REST Controller의 `ResponseEntity<ApiResponse<T>>` 규칙과는 별개입니다.

```java
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/send")
    public void sendMessage(ChatSendRequest request) {
        ChatMessageResponse response = chatService.sendMessage(request.getUserId(), request.getContent());
        messagingTemplate.convertAndSend("/sub/chat/room", response);
    }
}
```

- 클라이언트 구독 경로: `/sub/**`
- 메시지 전송 경로: `/pub/**` (WebSocket Config 기준)
