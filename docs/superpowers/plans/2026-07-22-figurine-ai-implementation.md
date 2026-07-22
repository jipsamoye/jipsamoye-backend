# AI 키캡 이미지 생성 (figurine) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 반려동물 사진을 OpenAI gpt-image-1로 키캡 굿즈 스타일 이미지로 변환하고, 버튼 한 번으로 자랑 피드에 자동 게시하는 비동기 API 3종(`POST /api/figurines`, `GET /api/figurines/{jobId}`, `POST /api/figurines/{jobId}/post`)을 구현한다.

**Architecture:** `FigurineJob` 엔티티가 PENDING→PROCESSING→COMPLETED/FAILED 상태를 가지며, `@Async` 프로세서가 S3 썸네일 다운로드 → OpenAI multipart 호출 → S3 결과 업로드를 수행한다. 프론트는 jobId로 폴링한다. 스펙: `docs/superpowers/plans/2026-07-22-figurine-ai.md`

**Tech Stack:** Spring Boot 3.5.13, Java 17, JPA(MySQL), AWS SDK v2 (`S3Client` 빈 재사용), `RestClient` + `MockRestServiceServer`, Mockito + AssertJ

## Global Constraints

- 작업 디렉터리: `/Users/jys/jipsamoye.backend.worktrees/figurine-ai` (브랜치 `feature/figurine-ai`) — 모든 경로는 이 디렉터리 기준 상대 경로
- 커밋 메시지는 한글, `feat:`/`fix:`/`docs:` 접두사 (CLAUDE.md)
- Entity는 `BaseEntity` 상속, `@Setter` 금지 (ArchUnit 규칙)
- DTO는 반드시 `dto/request/`·`dto/response/` 패키지 — `*Request`/`*Response` 네이밍 클래스가 다른 `..dto..` 위치에 있으면 ArchUnit 실패
- Controller는 `@RestController` + `ResponseEntity<ApiResponse<T>>`, 인증은 `@AuthenticationPrincipal CustomUserDetails userDetails` → `userDetails.getUserId()`
- Service는 인터페이스 + Impl 패턴
- 테스트 없이 커밋 금지, 각 태스크 끝에 `./gradlew test` 통과 확인 후 커밋
- 패키지 베이스: `com.jipsamoye.backend.domain.figurine`
- SecurityConfig 수정 불필요 — `anyRequest().authenticated()`가 기본이라 `/api/figurines/**`는 자동으로 인증 필수
- 스펙 대비 의도된 편차: `FigurineImageClient.generateKeycapImage`는 multipart 파일명이 필요해 3-파라미터 `(byte[] sourceImage, String contentType, String filename)`로 확장

---

### Task 0: 기존 스켈레톤 정리 + 계획 문서 커밋

2026-07-22 세션에서 중단된 기존 figurine 코드는 **전부 삭제하고 새로 구현**한다(스펙 명시, 사용자 지시). 현재 worktree에는 스테이징 상태로 존재한다.

**Files:**
- Delete: `src/main/java/com/jipsamoye/backend/domain/figurine/` (디렉터리 전체 — FigurineJob, FigurineStatus, FigurineJobRepository, FigurineImageClient, OpenAiImageResponse, OpenAiProperties)
- Keep & Commit: `docs/superpowers/plans/2026-07-22-figurine-ai.md`, `docs/superpowers/plans/2026-07-22-figurine-ai-implementation.md`

- [ ] **Step 1: figurine 소스 언스테이징 및 삭제**

```bash
cd /Users/jys/jipsamoye.backend.worktrees/figurine-ai
git restore --staged src/main/java/com/jipsamoye/backend/domain/figurine
rm -rf src/main/java/com/jipsamoye/backend/domain/figurine
```

- [ ] **Step 2: 상태 확인**

Run: `git status --short`
Expected: `A  docs/superpowers/plans/2026-07-22-figurine-ai.md`와 `?? docs/superpowers/plans/2026-07-22-figurine-ai-implementation.md`만 의미 있는 항목으로 남음 (`.DS_Store`류, `docs/code-review-2026-07/`는 무시)

- [ ] **Step 3: 계획 문서 커밋**

```bash
git add docs/superpowers/plans/2026-07-22-figurine-ai.md docs/superpowers/plans/2026-07-22-figurine-ai-implementation.md
git commit -m "docs: AI 키캡 이미지 생성 기능 스펙 및 구현 계획 추가"
```

---

### Task 1: FigurineStatus + FigurineJob 엔티티 + 리포지토리

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/entity/FigurineStatus.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/entity/FigurineJob.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/repository/FigurineJobRepository.java`
- Test: `src/test/java/com/jipsamoye/backend/domain/figurine/entity/FigurineJobTest.java`

**Interfaces:**
- Consumes: `com.jipsamoye.backend.global.entity.BaseEntity`, `com.jipsamoye.backend.domain.user.entity.User`
- Produces: `FigurineJob(User user, String sourceImageUrl)` 빌더 생성 → 상태 PENDING. 메서드 `startProcessing()`, `complete(String resultImageUrl)`, `fail(String reason)`, `linkPetPost(Long petPostId)`, `isInProgress()`. 게터: `getId()`, `getUser()`, `getStatus()`, `getSourceImageUrl()`, `getResultImageUrl()`, `getFailReason()`, `getPetPostId()`, (BaseEntity) `getCreatedAt()`. `FigurineJobRepository extends JpaRepository<FigurineJob, Long>`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/jipsamoye/backend/domain/figurine/entity/FigurineJobTest.java`:

```java
package com.jipsamoye.backend.domain.figurine.entity;

import com.jipsamoye.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigurineJobTest {

    private FigurineJob newJob() {
        return FigurineJob.builder()
                .user(Mockito.mock(User.class))
                .sourceImageUrl("https://images.jipsamoye.com/posts/1/abc.webp")
                .build();
    }

    @Test
    @DisplayName("생성 직후 상태는 PENDING이다")
    void initialStatus_isPending() {
        FigurineJob job = newJob();
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.PENDING);
        assertThat(job.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("startProcessing 호출 시 PROCESSING으로 전이한다")
    void startProcessing_transitionsToProcessing() {
        FigurineJob job = newJob();
        job.startProcessing();
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.PROCESSING);
        assertThat(job.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("complete 호출 시 COMPLETED로 전이하고 결과 URL을 저장한다")
    void complete_storesResultUrl() {
        FigurineJob job = newJob();
        job.startProcessing();
        job.complete("https://images.jipsamoye.com/posts/1/result.png");
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.COMPLETED);
        assertThat(job.getResultImageUrl()).isEqualTo("https://images.jipsamoye.com/posts/1/result.png");
        assertThat(job.getFailReason()).isNull();
        assertThat(job.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("fail 호출 시 FAILED로 전이하고 사유를 저장한다")
    void fail_storesReason() {
        FigurineJob job = newJob();
        job.startProcessing();
        job.fail("OpenAI API 호출 실패");
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.FAILED);
        assertThat(job.getFailReason()).isEqualTo("OpenAI API 호출 실패");
        assertThat(job.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("fail 사유가 500자를 넘으면 잘라서 저장한다")
    void fail_truncatesLongReason() {
        FigurineJob job = newJob();
        job.fail("가".repeat(600));
        assertThat(job.getFailReason()).hasSize(500);
    }

    @Test
    @DisplayName("COMPLETED 상태에서 linkPetPost가 petPostId를 연결한다")
    void linkPetPost_onCompleted_succeeds() {
        FigurineJob job = newJob();
        job.complete("https://images.jipsamoye.com/posts/1/result.png");
        job.linkPetPost(77L);
        assertThat(job.getPetPostId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("미완료 상태에서 linkPetPost는 IllegalStateException을 던진다")
    void linkPetPost_notCompleted_throws() {
        FigurineJob job = newJob();
        assertThatThrownBy(() -> job.linkPetPost(77L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 게시된 작업에 linkPetPost를 다시 호출하면 IllegalStateException을 던진다")
    void linkPetPost_alreadyLinked_throws() {
        FigurineJob job = newJob();
        job.complete("https://images.jipsamoye.com/posts/1/result.png");
        job.linkPetPost(77L);
        assertThatThrownBy(() -> job.linkPetPost(88L))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd /Users/jys/jipsamoye.backend.worktrees/figurine-ai && ./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.entity.FigurineJobTest'`
Expected: 컴파일 에러로 FAIL (FigurineJob, FigurineStatus 클래스 없음)

- [ ] **Step 3: 엔티티/리포지토리 구현**

`src/main/java/com/jipsamoye/backend/domain/figurine/entity/FigurineStatus.java`:

```java
package com.jipsamoye.backend.domain.figurine.entity;

public enum FigurineStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/entity/FigurineJob.java`:

```java
package com.jipsamoye.backend.domain.figurine.entity;

import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "figurine_job", indexes = {
        @Index(name = "idx_figurine_job_user", columnList = "user_id, created_at")
})
public class FigurineJob extends BaseEntity {

    private static final int FAIL_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FigurineStatus status = FigurineStatus.PENDING;

    @Column(nullable = false, length = 500)
    private String sourceImageUrl;

    @Column(length = 500)
    private String resultImageUrl;

    @Column(length = 500)
    private String failReason;

    // 자동 게시 후 연결되는 PetPost id — 중복 게시 방지 근거
    private Long petPostId;

    @Builder
    public FigurineJob(User user, String sourceImageUrl) {
        this.user = user;
        this.sourceImageUrl = sourceImageUrl;
        this.status = FigurineStatus.PENDING;
    }

    public void startProcessing() {
        this.status = FigurineStatus.PROCESSING;
    }

    public void complete(String resultImageUrl) {
        this.status = FigurineStatus.COMPLETED;
        this.resultImageUrl = resultImageUrl;
        this.failReason = null;
    }

    public void fail(String reason) {
        this.status = FigurineStatus.FAILED;
        this.failReason = truncate(reason);
    }

    public void linkPetPost(Long petPostId) {
        if (this.status != FigurineStatus.COMPLETED) {
            throw new IllegalStateException("완료되지 않은 작업은 게시할 수 없습니다.");
        }
        if (this.petPostId != null) {
            throw new IllegalStateException("이미 게시된 작업입니다.");
        }
        this.petPostId = petPostId;
    }

    public boolean isInProgress() {
        return status == FigurineStatus.PENDING || status == FigurineStatus.PROCESSING;
    }

    private String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() > FAIL_REASON_MAX_LENGTH ? reason.substring(0, FAIL_REASON_MAX_LENGTH) : reason;
    }
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/repository/FigurineJobRepository.java`:

```java
package com.jipsamoye.backend.domain.figurine.repository;

import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FigurineJobRepository extends JpaRepository<FigurineJob, Long> {
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.entity.FigurineJobTest'`
Expected: PASS (8 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/figurine src/test/java/com/jipsamoye/backend/domain/figurine
git commit -m "feat: figurine 잡 엔티티·상태·리포지토리 추가"
```

---

### Task 2: OpenAiProperties + FigurineAsyncConfig + yaml 설정

프로젝트에 `@EnableAsync`, `@EnableConfigurationProperties`, 범용 `ThreadPoolTaskExecutor`가 전무하므로 이 태스크에서 신규 도입한다. properties 등록은 `@ConfigurationPropertiesScan` 전역 스캔 대신 figurine 설정 클래스에 국소적으로 붙인다 (최소 인프라 원칙).

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/config/OpenAiProperties.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/config/FigurineAsyncConfig.java`
- Modify: `src/main/resources/application.yaml` (끝에 openai 블록 추가)
- Modify: `src/main/resources/application-local.yaml` (끝에 openai.api-key 추가)
- Modify: `src/main/resources/application-prod.yaml` (끝에 openai.api-key 추가)
- Test: `src/test/java/com/jipsamoye/backend/domain/figurine/config/FigurineAsyncConfigTest.java`

**Interfaces:**
- Produces: `OpenAiProperties(String apiKey, String model, String size, String quality)` record 빈, `figurineExecutor`라는 이름의 `ThreadPoolTaskExecutor` 빈, `@Async("figurineExecutor")` 사용 가능한 `@EnableAsync` 활성화

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/jipsamoye/backend/domain/figurine/config/FigurineAsyncConfigTest.java`:

```java
package com.jipsamoye.backend.domain.figurine.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class FigurineAsyncConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FigurineAsyncConfig.class)
            .withPropertyValues(
                    "openai.api-key=test-key",
                    "openai.model=gpt-image-1",
                    "openai.size=1024x1024",
                    "openai.quality=medium");

    @Test
    @DisplayName("openai 프로퍼티가 OpenAiProperties 빈에 바인딩된다")
    void bindsOpenAiProperties() {
        contextRunner.run(context -> {
            OpenAiProperties props = context.getBean(OpenAiProperties.class);
            assertThat(props.apiKey()).isEqualTo("test-key");
            assertThat(props.model()).isEqualTo("gpt-image-1");
            assertThat(props.size()).isEqualTo("1024x1024");
            assertThat(props.quality()).isEqualTo("medium");
        });
    }

    @Test
    @DisplayName("figurineExecutor 빈이 core 1 / max 2 / queue 20으로 생성된다")
    void createsFigurineExecutor() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean("figurineExecutor", ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getQueueCapacity()).isEqualTo(20);
        });
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.config.FigurineAsyncConfigTest'`
Expected: 컴파일 에러로 FAIL

- [ ] **Step 3: 구현**

`src/main/java/com/jipsamoye/backend/domain/figurine/config/OpenAiProperties.java`:

```java
package com.jipsamoye.backend.domain.figurine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String model,
        String size,
        String quality
) {
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/config/FigurineAsyncConfig.java`:

```java
package com.jipsamoye.backend.domain.figurine.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(OpenAiProperties.class)
public class FigurineAsyncConfig {

    @Bean(name = "figurineExecutor")
    public ThreadPoolTaskExecutor figurineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("figurine-");
        executor.initialize();
        return executor;
    }
}
```

`src/main/resources/application.yaml` 파일 끝에 추가:

```yaml

openai:
  model: gpt-image-1
  size: 1024x1024
  quality: medium
```

`src/main/resources/application-local.yaml` 파일 끝에 추가 (빈 기본값 — 키 없이도 로컬 부팅 가능):

```yaml

openai:
  api-key: ${OPENAI_API_KEY:}
```

`src/main/resources/application-prod.yaml` 파일 끝에 추가 (운영은 환경변수 필수):

```yaml

openai:
  api-key: ${OPENAI_API_KEY}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.config.FigurineAsyncConfigTest'`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/figurine/config src/test/java/com/jipsamoye/backend/domain/figurine/config src/main/resources
git commit -m "feat: OpenAI 설정 프로퍼티 및 figurine 전용 비동기 실행기 추가"
```

---

### Task 3: OpenAI 이미지 클라이언트

`NaverApiClient` 패턴(`src/main/java/com/jipsamoye/backend/domain/auth/client/NaverApiClient.java`) 준수: `RestClient.Builder` 주입 + 타임아웃 팩토리 + 테스트용 패키지-프라이빗 생성자 + `BusinessException` 재던짐 후 `RestClientException` 변환. multipart 아웃바운드는 프로젝트 최초 패턴이다.

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/client/FigurineImageClient.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/client/OpenAiFigurineImageClient.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/client/dto/response/OpenAiImageResponse.java`
- Modify: `src/main/java/com/jipsamoye/backend/global/code/ErrorCode.java` (502 섹션에 항목 추가)
- Test: `src/test/java/com/jipsamoye/backend/domain/figurine/client/OpenAiFigurineImageClientTest.java`

**Interfaces:**
- Consumes: `OpenAiProperties` (Task 2), `BusinessException(ErrorCode, String)`
- Produces: `FigurineImageClient` 인터페이스 — `byte[] generateKeycapImage(byte[] sourceImage, String contentType, String filename)`. 실패 시 `BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED)`. `ErrorCode.FIGURINE_GENERATION_FAILED` 신규 상수

- [ ] **Step 1: ErrorCode에 FIGURINE_GENERATION_FAILED 추가**

`src/main/java/com/jipsamoye/backend/global/code/ErrorCode.java`의 502 섹션을 다음으로 교체 (마지막 항목의 `;` 위치 주의):

```java
    // 502
    NAVER_API_ERROR(502, "NAVER_API_ERROR", "네이버 API 호출에 실패했습니다."),
    FIGURINE_GENERATION_FAILED(502, "FIGURINE_GENERATION_FAILED", "AI 이미지 생성에 실패했습니다.");
```

- [ ] **Step 2: 실패하는 테스트 작성**

`src/test/java/com/jipsamoye/backend/domain/figurine/client/OpenAiFigurineImageClientTest.java`:

```java
package com.jipsamoye.backend.domain.figurine.client;

import com.jipsamoye.backend.domain.figurine.config.OpenAiProperties;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiFigurineImageClientTest {

    private static final String EDITS_URL = "https://api.openai.com/v1/images/edits";
    private static final OpenAiProperties PROPERTIES =
            new OpenAiProperties("test-api-key", "gpt-image-1", "1024x1024", "medium");

    private MockRestServiceServer mockServer;
    private OpenAiFigurineImageClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiFigurineImageClient(builder.build(), PROPERTIES);
    }

    @Test
    @DisplayName("정상 응답이면 b64_json을 디코드한 이미지 바이트를 반환한다")
    void generateKeycapImage_success() {
        byte[] expected = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
        String b64 = Base64.getEncoder().encodeToString(expected);
        mockServer.expect(requestTo(EDITS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("""
                        { "created": 1721600000, "data": [ { "b64_json": "%s" } ] }
                        """.formatted(b64), MediaType.APPLICATION_JSON));

        byte[] result = client.generateKeycapImage(
                "source".getBytes(StandardCharsets.UTF_8), "image/webp", "source.webp");

        assertThat(result).isEqualTo(expected);
        mockServer.verify();
    }

    @Test
    @DisplayName("응답 data가 비어 있으면 FIGURINE_GENERATION_FAILED 예외를 던진다")
    void generateKeycapImage_emptyData_throws() {
        mockServer.expect(requestTo(EDITS_URL))
                .andRespond(withSuccess("""
                        { "created": 1721600000, "data": [] }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateKeycapImage(
                "source".getBytes(StandardCharsets.UTF_8), "image/webp", "source.webp"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_GENERATION_FAILED));
    }

    @Test
    @DisplayName("서버 오류 응답이면 FIGURINE_GENERATION_FAILED 예외를 던진다")
    void generateKeycapImage_serverError_throws() {
        mockServer.expect(requestTo(EDITS_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.generateKeycapImage(
                "source".getBytes(StandardCharsets.UTF_8), "image/webp", "source.webp"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_GENERATION_FAILED));
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.client.OpenAiFigurineImageClientTest'`
Expected: 컴파일 에러로 FAIL

- [ ] **Step 4: 클라이언트 구현**

`src/main/java/com/jipsamoye/backend/domain/figurine/client/dto/response/OpenAiImageResponse.java`:

```java
package com.jipsamoye.backend.domain.figurine.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiImageResponse(List<ImageData> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageData(@JsonProperty("b64_json") String b64Json) {
    }

    public boolean hasImage() {
        return data != null && !data.isEmpty() && data.get(0).b64Json() != null;
    }

    public String firstB64Json() {
        return data.get(0).b64Json();
    }
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/client/FigurineImageClient.java`:

```java
package com.jipsamoye.backend.domain.figurine.client;

/**
 * 반려동물 사진을 키캡 굿즈 스타일 이미지로 변환하는 클라이언트.
 */
public interface FigurineImageClient {

    /**
     * 입력 이미지를 키캡 피규어 스타일로 변환한 PNG 바이트를 반환한다.
     *
     * @param sourceImage 입력 이미지 바이트
     * @param contentType 입력 이미지 Content-Type (image/webp 등)
     * @param filename    multipart 전송용 파일명
     */
    byte[] generateKeycapImage(byte[] sourceImage, String contentType, String filename);
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/client/OpenAiFigurineImageClient.java`:

```java
package com.jipsamoye.backend.domain.figurine.client;

import com.jipsamoye.backend.domain.figurine.client.dto.response.OpenAiImageResponse;
import com.jipsamoye.backend.domain.figurine.config.OpenAiProperties;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;

@Component
public class OpenAiFigurineImageClient implements FigurineImageClient {

    private static final String EDITS_URL = "https://api.openai.com/v1/images/edits";
    private static final String KEYCAP_PROMPT = """
            Transform the pet in this photo into an adorable chibi-style miniature figurine \
            sculpted on top of an artisan mechanical keyboard keycap. \
            Product photography style, soft studio lighting, glossy resin texture, \
            keyboard visible blurred in the background.""";

    private final RestClient restClient;
    private final OpenAiProperties properties;

    @Autowired
    public OpenAiFigurineImageClient(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.properties = properties;
    }

    OpenAiFigurineImageClient(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public byte[] generateKeycapImage(byte[] sourceImage, String contentType, String filename) {
        try {
            OpenAiImageResponse response = restClient.post()
                    .uri(EDITS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(buildMultipartBody(sourceImage, contentType, filename))
                    .retrieve()
                    .body(OpenAiImageResponse.class);

            if (response == null || !response.hasImage()) {
                throw new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "OpenAI 응답에 이미지가 없습니다.");
            }
            return Base64.getDecoder().decode(response.firstB64Json());
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "OpenAI API 호출 실패: " + e.getMessage());
        }
    }

    private MultiValueMap<String, Object> buildMultipartBody(byte[] sourceImage, String contentType, String filename) {
        ByteArrayResource imageResource = new ByteArrayResource(sourceImage) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(contentType));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new HttpEntity<>(imageResource, imageHeaders));
        body.add("model", properties.model());
        body.add("prompt", KEYCAP_PROMPT);
        body.add("size", properties.size());
        body.add("quality", properties.quality());
        body.add("n", "1");
        return body;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.client.OpenAiFigurineImageClientTest'`
Expected: PASS (3 tests)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/figurine/client src/test/java/com/jipsamoye/backend/domain/figurine/client src/main/java/com/jipsamoye/backend/global/code/ErrorCode.java
git commit -m "feat: OpenAI gpt-image-1 키캡 이미지 생성 클라이언트 추가"
```

---

### Task 4: FigurineImageStorage (S3 입출력)

기존 `S3Client` 빈(`global/config/S3Config.java`, AWS SDK v2) 재사용. CDN 경유 조회 금지 — Worker가 4xx를 60초 캐시하므로 S3 직접 접근. `extractKeyFromUrl` 로직은 `ImageServiceImpl`의 private 메서드(line 145-156)와 동일하게 복제한다 (public 승격은 이번 범위 밖). 썸네일 재시도 지연은 테스트에서 0ms로 주입할 수 있도록 패키지-프라이빗 생성자를 둔다.

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/storage/FigurineSourceImage.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/storage/FigurineImageStorage.java`
- Test: `src/test/java/com/jipsamoye/backend/domain/figurine/storage/FigurineImageStorageTest.java`

**Interfaces:**
- Consumes: `software.amazon.awssdk.services.s3.S3Client` 빈, `ErrorCode.FIGURINE_GENERATION_FAILED` (Task 3), `ErrorCode.S3_UPLOAD_ERROR`(기존), `ErrorCode.BAD_REQUEST`(기존)
- Produces: `FigurineSourceImage(byte[] bytes, String contentType, String filename)` record. `FigurineImageStorage.downloadSource(String sourceImageUrl)` → `FigurineSourceImage`, `FigurineImageStorage.uploadResult(Long userId, byte[] png)` → CDN URL 문자열

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/jipsamoye/backend/domain/figurine/storage/FigurineImageStorageTest.java`:

```java
package com.jipsamoye.backend.domain.figurine.storage;

import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FigurineImageStorageTest {

    private static final String CDN = "https://images.jipsamoye.com";
    private static final String SOURCE_URL = CDN + "/posts/42/abc-uuid.jpg";

    @Mock
    private S3Client s3Client;

    private FigurineImageStorage storage;

    @BeforeEach
    void setUp() {
        // 재시도 지연 0ms로 주입 (테스트 전용 생성자)
        storage = new FigurineImageStorage(s3Client, "test-bucket", "ap-northeast-2", CDN, 0L);
    }

    private ResponseBytes<GetObjectResponse> bytesOf(String content) {
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("800px 썸네일이 존재하면 webp 소스로 반환한다")
    void downloadSource_thumbnailExists() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(bytesOf("thumb"));

        FigurineSourceImage source = storage.downloadSource(SOURCE_URL);

        assertThat(source.contentType()).isEqualTo("image/webp");
        assertThat(source.filename()).isEqualTo("source.webp");
        assertThat(source.bytes()).isEqualTo("thumb".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("posts/42/thumbnails/abc-uuid_800.webp");
    }

    @Test
    @DisplayName("썸네일이 늦게 생성되면 재시도 후 성공한다")
    void downloadSource_thumbnailAppearsAfterRetry() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenReturn(bytesOf("thumb"));

        FigurineSourceImage source = storage.downloadSource(SOURCE_URL);

        assertThat(source.contentType()).isEqualTo("image/webp");
        verify(s3Client, times(3)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("썸네일이 5회 모두 없으면 원본으로 폴백한다")
    void downloadSource_fallsBackToOriginal() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenReturn(bytesOf("original"));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(1024L).contentType("image/jpeg").build());

        FigurineSourceImage source = storage.downloadSource(SOURCE_URL);

        assertThat(source.contentType()).isEqualTo("image/jpeg");
        assertThat(source.filename()).isEqualTo("abc-uuid.jpg");
        assertThat(source.bytes()).isEqualTo("original".getBytes(StandardCharsets.UTF_8));
        verify(s3Client, times(6)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("원본이 10MB를 초과하면 FIGURINE_GENERATION_FAILED 예외를 던진다")
    void downloadSource_originalTooLarge_throws() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(11L * 1024 * 1024).contentType("image/jpeg").build());

        assertThatThrownBy(() -> storage.downloadSource(SOURCE_URL))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_GENERATION_FAILED));
    }

    @Test
    @DisplayName("우리 CDN/S3 URL이 아니면 BAD_REQUEST 예외를 던진다")
    void downloadSource_foreignUrl_throws() {
        assertThatThrownBy(() -> storage.downloadSource("https://evil.example.com/cat.jpg"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("uploadResult는 posts/{userId}/{uuid}.png로 업로드하고 CDN URL을 반환한다")
    void uploadResult_uploadsAndReturnsCdnUrl() {
        byte[] png = "png".getBytes(StandardCharsets.UTF_8);

        String url = storage.uploadResult(42L, png);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).matches("posts/42/[0-9a-f-]{36}\\.png");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo(CDN + "/" + captor.getValue().key());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.storage.FigurineImageStorageTest'`
Expected: 컴파일 에러로 FAIL

- [ ] **Step 3: 구현**

`src/main/java/com/jipsamoye/backend/domain/figurine/storage/FigurineSourceImage.java`:

```java
package com.jipsamoye.backend.domain.figurine.storage;

/**
 * OpenAI에 전달할 원본(또는 썸네일) 이미지 바이트와 메타데이터.
 */
public record FigurineSourceImage(byte[] bytes, String contentType, String filename) {
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/storage/FigurineImageStorage.java`:

```java
package com.jipsamoye.backend.domain.figurine.storage;

import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * figurine 생성용 S3 입출력. CDN 경유 조회 금지 — Worker가 4xx를 60초 캐시하므로 S3 직접 접근.
 */
@Slf4j
@Component
public class FigurineImageStorage {

    private static final int THUMBNAIL_MAX_ATTEMPTS = 5;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 2_000L;
    private static final long MAX_ORIGINAL_BYTES = 10L * 1024 * 1024;

    private final S3Client s3Client;
    private final String bucket;
    private final String region;
    private final String cdnBaseUrl;
    private final long retryDelayMillis;

    @Autowired
    public FigurineImageStorage(S3Client s3Client,
                                @Value("${cloud.aws.s3.bucket}") String bucket,
                                @Value("${cloud.aws.region.static}") String region,
                                @Value("${cdn.image-base-url:https://images.jipsamoye.com}") String cdnBaseUrl) {
        this(s3Client, bucket, region, cdnBaseUrl, DEFAULT_RETRY_DELAY_MILLIS);
    }

    FigurineImageStorage(S3Client s3Client, String bucket, String region, String cdnBaseUrl, long retryDelayMillis) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
        this.cdnBaseUrl = cdnBaseUrl;
        this.retryDelayMillis = retryDelayMillis;
    }

    /**
     * Lambda가 생성한 800px 썸네일을 우선 조회(2초 간격 최대 5회)하고, 없으면 원본으로 폴백한다.
     * 원본은 10MB 초과 시 거부.
     */
    public FigurineSourceImage downloadSource(String sourceImageUrl) {
        String originalKey = extractKeyFromUrl(sourceImageUrl);
        if (originalKey == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "지원하지 않는 이미지 URL입니다.");
        }
        String thumbnailKey = toThumbnailKey(originalKey);
        if (thumbnailKey != null) {
            for (int attempt = 1; attempt <= THUMBNAIL_MAX_ATTEMPTS; attempt++) {
                try {
                    byte[] bytes = getObject(thumbnailKey);
                    return new FigurineSourceImage(bytes, "image/webp", "source.webp");
                } catch (NoSuchKeyException e) {
                    log.info("썸네일 미생성, 재시도 {}/{}: {}", attempt, THUMBNAIL_MAX_ATTEMPTS, thumbnailKey);
                    if (attempt < THUMBNAIL_MAX_ATTEMPTS) {
                        sleep();
                    }
                }
            }
        }
        return downloadOriginal(originalKey);
    }

    /**
     * 결과 PNG를 posts/{userId}/{uuid}.png로 업로드하고 CDN URL을 반환한다.
     * posts 경로 재사용으로 Lambda 썸네일·삭제 로직이 그대로 동작한다.
     */
    public String uploadResult(Long userId, byte[] png) {
        String key = "posts/" + userId + "/" + UUID.randomUUID() + ".png";
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("image/png")
                            .build(),
                    RequestBody.fromBytes(png));
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.S3_UPLOAD_ERROR, "결과 이미지 업로드 실패: " + e.getMessage());
        }
        return cdnBaseUrl + "/" + key;
    }

    private FigurineSourceImage downloadOriginal(String originalKey) {
        try {
            HeadObjectResponse head = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(originalKey).build());
            if (head.contentLength() != null && head.contentLength() > MAX_ORIGINAL_BYTES) {
                throw new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "원본 이미지가 10MB를 초과합니다.");
            }
            byte[] bytes = getObject(originalKey);
            String filename = originalKey.substring(originalKey.lastIndexOf('/') + 1);
            String contentType = head.contentType() != null ? head.contentType() : "image/jpeg";
            return new FigurineSourceImage(bytes, contentType, filename);
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED,
                    "원본 이미지를 찾을 수 없습니다: " + originalKey);
        }
    }

    private byte[] getObject(String key) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    // ImageServiceImpl.extractKeyFromUrl과 동일 로직 (private라 복제)
    private String extractKeyFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String s3Prefix = String.format("https://%s.s3.%s.amazonaws.com/", bucket, region);
        String cdnPrefix = cdnBaseUrl + "/";
        if (imageUrl.startsWith(cdnPrefix)) {
            return imageUrl.substring(cdnPrefix.length());
        }
        if (imageUrl.startsWith(s3Prefix)) {
            return imageUrl.substring(s3Prefix.length());
        }
        return null;
    }

    // posts/42/abc.jpg → posts/42/thumbnails/abc_800.webp (S3 경로 규약, docs/INFRASTRUCTURE.md)
    private String toThumbnailKey(String key) {
        int slash = key.lastIndexOf('/');
        int dot = key.lastIndexOf('.');
        if (slash < 0 || dot <= slash) {
            return null;
        }
        return key.substring(0, slash) + "/thumbnails/" + key.substring(slash + 1, dot) + "_800.webp";
    }

    private void sleep() {
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "이미지 다운로드 대기 중 인터럽트되었습니다.");
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.storage.FigurineImageStorageTest'`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/figurine/storage src/test/java/com/jipsamoye/backend/domain/figurine/storage
git commit -m "feat: figurine S3 썸네일 다운로드·결과 업로드 컴포넌트 추가"
```

---

### Task 5: FigurineJobProcessor (비동기 파이프라인)

`@Async("figurineExecutor")` 메서드. 짧은 트랜잭션 분리: 각 `repository.save()`가 자체 트랜잭션이고, S3/OpenAI 호출은 트랜잭션 밖에서 수행된다. `userId`를 파라미터로 받는 이유: LAZY `job.getUser()`를 트랜잭션 없는 비동기 스레드에서 터치하면 `LazyInitializationException`이 나기 때문.

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/service/FigurineJobProcessor.java`
- Test: `src/test/java/com/jipsamoye/backend/domain/figurine/service/FigurineJobProcessorTest.java`

**Interfaces:**
- Consumes: `FigurineJobRepository` (Task 1), `FigurineImageStorage.downloadSource/uploadResult` (Task 4), `FigurineImageClient.generateKeycapImage` (Task 3)
- Produces: `void process(Long jobId, Long userId)` — 호출 후 job이 COMPLETED(resultImageUrl) 또는 FAILED(failReason)로 저장됨. 테스트에서는 프록시 없이 직접 호출하면 동기 실행됨

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/jipsamoye/backend/domain/figurine/service/FigurineJobProcessorTest.java`:

```java
package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.client.FigurineImageClient;
import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.entity.FigurineStatus;
import com.jipsamoye.backend.domain.figurine.repository.FigurineJobRepository;
import com.jipsamoye.backend.domain.figurine.storage.FigurineImageStorage;
import com.jipsamoye.backend.domain.figurine.storage.FigurineSourceImage;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FigurineJobProcessorTest {

    private static final String SOURCE_URL = "https://images.jipsamoye.com/posts/42/abc.jpg";
    private static final String RESULT_URL = "https://images.jipsamoye.com/posts/42/result.png";

    @InjectMocks
    private FigurineJobProcessor processor;

    @Mock
    private FigurineJobRepository figurineJobRepository;
    @Mock
    private FigurineImageStorage figurineImageStorage;
    @Mock
    private FigurineImageClient figurineImageClient;

    private FigurineJob newJob() {
        return FigurineJob.builder()
                .user(Mockito.mock(User.class))
                .sourceImageUrl(SOURCE_URL)
                .build();
    }

    @Test
    @DisplayName("성공 시 job이 COMPLETED로 저장되고 결과 URL이 연결된다")
    void process_success_completesJob() {
        FigurineJob job = newJob();
        byte[] source = "source".getBytes(StandardCharsets.UTF_8);
        byte[] result = "result-png".getBytes(StandardCharsets.UTF_8);
        when(figurineJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(figurineImageStorage.downloadSource(SOURCE_URL))
                .thenReturn(new FigurineSourceImage(source, "image/webp", "source.webp"));
        when(figurineImageClient.generateKeycapImage(source, "image/webp", "source.webp")).thenReturn(result);
        when(figurineImageStorage.uploadResult(42L, result)).thenReturn(RESULT_URL);

        processor.process(1L, 42L);

        assertThat(job.getStatus()).isEqualTo(FigurineStatus.COMPLETED);
        assertThat(job.getResultImageUrl()).isEqualTo(RESULT_URL);
        verify(figurineJobRepository, times(2)).save(job);
    }

    @Test
    @DisplayName("OpenAI 호출이 실패하면 job이 FAILED로 저장되고 사유가 남는다")
    void process_clientFails_marksFailed() {
        FigurineJob job = newJob();
        when(figurineJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(figurineImageStorage.downloadSource(SOURCE_URL))
                .thenReturn(new FigurineSourceImage("s".getBytes(StandardCharsets.UTF_8), "image/webp", "source.webp"));
        when(figurineImageClient.generateKeycapImage(any(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "OpenAI API 호출 실패"));

        processor.process(1L, 42L);

        assertThat(job.getStatus()).isEqualTo(FigurineStatus.FAILED);
        assertThat(job.getFailReason()).contains("OpenAI API 호출 실패");
        verify(figurineJobRepository, times(2)).save(job);
    }

    @Test
    @DisplayName("S3 다운로드가 실패해도 job이 FAILED로 저장된다")
    void process_downloadFails_marksFailed() {
        FigurineJob job = newJob();
        when(figurineJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(figurineImageStorage.downloadSource(SOURCE_URL))
                .thenThrow(new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "원본 이미지를 찾을 수 없습니다"));

        processor.process(1L, 42L);

        assertThat(job.getStatus()).isEqualTo(FigurineStatus.FAILED);
        assertThat(job.getFailReason()).contains("원본 이미지를 찾을 수 없습니다");
        verifyNoInteractions(figurineImageClient);
    }

    @Test
    @DisplayName("job이 존재하지 않으면 아무 작업도 하지 않는다")
    void process_jobNotFound_returns() {
        when(figurineJobRepository.findById(anyLong())).thenReturn(Optional.empty());

        processor.process(999L, 42L);

        verifyNoInteractions(figurineImageStorage, figurineImageClient);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.service.FigurineJobProcessorTest'`
Expected: 컴파일 에러로 FAIL

- [ ] **Step 3: 구현**

`src/main/java/com/jipsamoye/backend/domain/figurine/service/FigurineJobProcessor.java`:

```java
package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.client.FigurineImageClient;
import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.repository.FigurineJobRepository;
import com.jipsamoye.backend.domain.figurine.storage.FigurineImageStorage;
import com.jipsamoye.backend.domain.figurine.storage.FigurineSourceImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FigurineJobProcessor {

    private final FigurineJobRepository figurineJobRepository;
    private final FigurineImageStorage figurineImageStorage;
    private final FigurineImageClient figurineImageClient;

    /**
     * S3 다운로드 → OpenAI 변환 → S3 업로드를 비동기 수행한다.
     * 트랜잭션을 열지 않는다 — 외부 I/O(최대 2분)가 커넥션을 점유하지 않도록 save 단위로만 커밋.
     *
     * @param userId LAZY user를 트랜잭션 밖에서 터치하지 않기 위해 별도 전달
     */
    @Async("figurineExecutor")
    public void process(Long jobId, Long userId) {
        FigurineJob job = figurineJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("figurine job을 찾을 수 없습니다: jobId={}", jobId);
            return;
        }
        job.startProcessing();
        figurineJobRepository.save(job);
        try {
            FigurineSourceImage source = figurineImageStorage.downloadSource(job.getSourceImageUrl());
            byte[] resultPng = figurineImageClient.generateKeycapImage(
                    source.bytes(), source.contentType(), source.filename());
            String resultImageUrl = figurineImageStorage.uploadResult(userId, resultPng);
            job.complete(resultImageUrl);
        } catch (Exception e) {
            log.error("figurine 생성 실패: jobId={}", jobId, e);
            job.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        figurineJobRepository.save(job);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.service.FigurineJobProcessorTest'`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/figurine/service src/test/java/com/jipsamoye/backend/domain/figurine/service
git commit -m "feat: figurine 비동기 잡 프로세서 추가"
```

---

### Task 6: FigurineService + DTO + ErrorCode

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/dto/request/FigurineJobCreateRequest.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/dto/response/FigurineJobResponse.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/dto/response/FigurinePublishResponse.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/service/FigurineService.java`
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/service/FigurineServiceImpl.java`
- Modify: `src/main/java/com/jipsamoye/backend/global/code/ErrorCode.java` (400/404/409 섹션에 항목 추가)
- Test: `src/test/java/com/jipsamoye/backend/domain/figurine/service/FigurineServiceImplTest.java`

**Interfaces:**
- Consumes: `FigurineJobRepository`, `FigurineJob` (Task 1), `FigurineJobProcessor.process(jobId, userId)` (Task 5), 기존 `UserRepository.findById`, 기존 `PetPostService.createPost(PetPostCreateRequest, Long)` → `PetPostResponse`(record, `id()` 접근자), `PetPostCreateRequest(String title, String content, List<String> imageUrls)`
- Produces: `FigurineService` — `FigurineJobResponse createJob(FigurineJobCreateRequest, Long userId)`, `FigurineJobResponse getJob(Long jobId, Long userId)`, `FigurinePublishResponse publishJob(Long jobId, Long userId)`. `FigurineJobResponse(Long jobId, FigurineStatus status, String resultImageUrl, String failReason, Long petPostId)` + `from(FigurineJob)`. `FigurinePublishResponse(Long petPostId)`. ErrorCode 신규 3종: `FIGURINE_JOB_NOT_COMPLETED(400)`, `FIGURINE_JOB_NOT_FOUND(404)`, `FIGURINE_ALREADY_POSTED(409)`

- [ ] **Step 1: ErrorCode 3종 추가**

`src/main/java/com/jipsamoye/backend/global/code/ErrorCode.java` 각 섹션 마지막에 추가:

400 섹션 (`MISSING_PARAMETER` 뒤):
```java
    FIGURINE_JOB_NOT_COMPLETED(400, "FIGURINE_JOB_NOT_COMPLETED", "아직 완료되지 않은 생성 작업입니다."),
```

404 섹션 (`BOARD_COMMENT_NOT_FOUND` 뒤):
```java
    FIGURINE_JOB_NOT_FOUND(404, "FIGURINE_JOB_NOT_FOUND", "생성 작업을 찾을 수 없습니다."),
```

409 섹션 (`DUPLICATE_LIKE` 뒤):
```java
    FIGURINE_ALREADY_POSTED(409, "FIGURINE_ALREADY_POSTED", "이미 게시된 생성 작업입니다."),
```

- [ ] **Step 2: 실패하는 테스트 작성**

`src/test/java/com/jipsamoye/backend/domain/figurine/service/FigurineServiceImplTest.java`:

```java
package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.dto.request.FigurineJobCreateRequest;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurineJobResponse;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurinePublishResponse;
import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.entity.FigurineStatus;
import com.jipsamoye.backend.domain.figurine.repository.FigurineJobRepository;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostCreateRequest;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostResponse;
import com.jipsamoye.backend.domain.petPost.service.PetPostService;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FigurineServiceImplTest {

    private static final String CDN = "https://images.jipsamoye.com";
    private static final String SOURCE_URL = CDN + "/posts/42/abc.jpg";
    private static final String RESULT_URL = CDN + "/posts/42/result.png";

    @InjectMocks
    private FigurineServiceImpl figurineService;

    @Mock
    private FigurineJobRepository figurineJobRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FigurineJobProcessor figurineJobProcessor;
    @Mock
    private PetPostService petPostService;
    @Mock
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(figurineService, "cdnBaseUrl", CDN);
    }

    private FigurineJob ownedJob(Long jobId, Long ownerId) {
        when(user.getId()).thenReturn(ownerId);
        FigurineJob job = FigurineJob.builder().user(user).sourceImageUrl(SOURCE_URL).build();
        ReflectionTestUtils.setField(job, "id", jobId);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.now());
        when(figurineJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        return job;
    }

    @Test
    @DisplayName("createJob은 job을 저장하고 비동기 프로세서를 호출한 뒤 PENDING을 반환한다")
    void createJob_savesAndTriggersProcessor() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(figurineJobRepository.save(any(FigurineJob.class))).thenAnswer(invocation -> {
            FigurineJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 1L);
            return job;
        });

        FigurineJobResponse response = figurineService.createJob(new FigurineJobCreateRequest(SOURCE_URL), 42L);

        assertThat(response.jobId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(FigurineStatus.PENDING);
        verify(figurineJobProcessor).process(1L, 42L);
    }

    @Test
    @DisplayName("createJob은 우리 CDN URL이 아니면 BAD_REQUEST를 던진다")
    void createJob_foreignUrl_throws() {
        assertThatThrownBy(() -> figurineService.createJob(
                new FigurineJobCreateRequest("https://evil.example.com/cat.jpg"), 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verify(figurineJobProcessor, never()).process(any(), any());
    }

    @Test
    @DisplayName("getJob은 본인 소유가 아니면 FORBIDDEN을 던진다")
    void getJob_notOwner_throws() {
        ownedJob(1L, 42L);

        assertThatThrownBy(() -> figurineService.getJob(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("getJob은 존재하지 않는 job이면 FIGURINE_JOB_NOT_FOUND를 던진다")
    void getJob_notFound_throws() {
        when(figurineJobRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> figurineService.getJob(1L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_JOB_NOT_FOUND));
    }

    @Test
    @DisplayName("getJob은 5분 넘게 진행 중인 job을 FAILED로 전환한다")
    void getJob_staleInProgress_marksFailed() {
        FigurineJob job = ownedJob(1L, 42L);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.now().minusMinutes(6));

        FigurineJobResponse response = figurineService.getJob(1L, 42L);

        assertThat(response.status()).isEqualTo(FigurineStatus.FAILED);
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.FAILED);
    }

    @Test
    @DisplayName("getJob은 5분 이내 진행 중인 job을 그대로 반환한다")
    void getJob_freshInProgress_returnsAsIs() {
        ownedJob(1L, 42L);

        FigurineJobResponse response = figurineService.getJob(1L, 42L);

        assertThat(response.status()).isEqualTo(FigurineStatus.PENDING);
    }

    @Test
    @DisplayName("publishJob은 완료된 job으로 petPost를 만들고 petPostId를 연결한다")
    void publishJob_success() {
        FigurineJob job = ownedJob(1L, 42L);
        job.complete(RESULT_URL);
        PetPostResponse post = new PetPostResponse(77L, "AI 키캡 자랑", null, List.of(RESULT_URL),
                0, 0, "집사", null, LocalDateTime.now(), LocalDateTime.now());
        when(petPostService.createPost(any(PetPostCreateRequest.class), any(Long.class))).thenReturn(post);

        FigurinePublishResponse response = figurineService.publishJob(1L, 42L);

        assertThat(response.petPostId()).isEqualTo(77L);
        assertThat(job.getPetPostId()).isEqualTo(77L);
        ArgumentCaptor<PetPostCreateRequest> captor = ArgumentCaptor.forClass(PetPostCreateRequest.class);
        verify(petPostService).createPost(captor.capture(), any(Long.class));
        assertThat(captor.getValue().title()).isEqualTo("AI 키캡 자랑");
        assertThat(captor.getValue().imageUrls()).containsExactly(RESULT_URL);
    }

    @Test
    @DisplayName("publishJob은 미완료 job이면 FIGURINE_JOB_NOT_COMPLETED를 던진다")
    void publishJob_notCompleted_throws() {
        ownedJob(1L, 42L);

        assertThatThrownBy(() -> figurineService.publishJob(1L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_JOB_NOT_COMPLETED));
    }

    @Test
    @DisplayName("publishJob은 이미 게시된 job이면 FIGURINE_ALREADY_POSTED를 던진다")
    void publishJob_alreadyPosted_throws() {
        FigurineJob job = ownedJob(1L, 42L);
        job.complete(RESULT_URL);
        job.linkPetPost(77L);

        assertThatThrownBy(() -> figurineService.publishJob(1L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_ALREADY_POSTED));
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.service.FigurineServiceImplTest'`
Expected: 컴파일 에러로 FAIL

- [ ] **Step 4: DTO/서비스 구현**

`src/main/java/com/jipsamoye/backend/domain/figurine/dto/request/FigurineJobCreateRequest.java`:

```java
package com.jipsamoye.backend.domain.figurine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FigurineJobCreateRequest(
        @NotBlank @Size(max = 500) String sourceImageUrl
) {
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/dto/response/FigurineJobResponse.java`:

```java
package com.jipsamoye.backend.domain.figurine.dto.response;

import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.entity.FigurineStatus;

public record FigurineJobResponse(
        Long jobId,
        FigurineStatus status,
        String resultImageUrl,
        String failReason,
        Long petPostId
) {
    public static FigurineJobResponse from(FigurineJob job) {
        return new FigurineJobResponse(
                job.getId(),
                job.getStatus(),
                job.getResultImageUrl(),
                job.getFailReason(),
                job.getPetPostId()
        );
    }
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/dto/response/FigurinePublishResponse.java`:

```java
package com.jipsamoye.backend.domain.figurine.dto.response;

public record FigurinePublishResponse(Long petPostId) {
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/service/FigurineService.java`:

```java
package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.dto.request.FigurineJobCreateRequest;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurineJobResponse;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurinePublishResponse;

public interface FigurineService {

    FigurineJobResponse createJob(FigurineJobCreateRequest request, Long userId);

    FigurineJobResponse getJob(Long jobId, Long userId);

    FigurinePublishResponse publishJob(Long jobId, Long userId);
}
```

`src/main/java/com/jipsamoye/backend/domain/figurine/service/FigurineServiceImpl.java`:

```java
package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.dto.request.FigurineJobCreateRequest;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurineJobResponse;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurinePublishResponse;
import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.entity.FigurineStatus;
import com.jipsamoye.backend.domain.figurine.repository.FigurineJobRepository;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostCreateRequest;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostResponse;
import com.jipsamoye.backend.domain.petPost.service.PetPostService;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FigurineServiceImpl implements FigurineService {

    private static final Duration JOB_TIMEOUT = Duration.ofMinutes(5);
    private static final String AUTO_POST_TITLE = "AI 키캡 자랑";

    private final FigurineJobRepository figurineJobRepository;
    private final UserRepository userRepository;
    private final FigurineJobProcessor figurineJobProcessor;
    private final PetPostService petPostService;

    @Value("${cdn.image-base-url:https://images.jipsamoye.com}")
    private String cdnBaseUrl;

    /**
     * 의도적으로 @Transactional 없음 — save가 자체 트랜잭션으로 먼저 커밋된 뒤
     * 비동기 프로세서를 호출해야 프로세서 스레드가 커밋 전 job을 못 찾는 레이스가 없다.
     */
    @Override
    public FigurineJobResponse createJob(FigurineJobCreateRequest request, Long userId) {
        if (!request.sourceImageUrl().startsWith(cdnBaseUrl + "/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "지원하지 않는 이미지 URL입니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        FigurineJob job = figurineJobRepository.save(FigurineJob.builder()
                .user(user)
                .sourceImageUrl(request.sourceImageUrl())
                .build());
        figurineJobProcessor.process(job.getId(), userId);
        return FigurineJobResponse.from(job);
    }

    @Override
    @Transactional
    public FigurineJobResponse getJob(Long jobId, Long userId) {
        FigurineJob job = findOwnedJob(jobId, userId);
        // 서버 재시작 등으로 유실된 job 방어: 5분 초과 진행 중이면 FAILED 전환 (더티체킹으로 저장)
        if (job.isInProgress() && job.getCreatedAt().isBefore(LocalDateTime.now().minus(JOB_TIMEOUT))) {
            job.fail("처리 시간이 5분을 초과했습니다. 다시 시도해주세요.");
        }
        return FigurineJobResponse.from(job);
    }

    @Override
    @Transactional
    public FigurinePublishResponse publishJob(Long jobId, Long userId) {
        FigurineJob job = findOwnedJob(jobId, userId);
        if (job.getPetPostId() != null) {
            throw new BusinessException(ErrorCode.FIGURINE_ALREADY_POSTED);
        }
        if (job.getStatus() != FigurineStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.FIGURINE_JOB_NOT_COMPLETED);
        }
        PetPostResponse post = petPostService.createPost(
                new PetPostCreateRequest(AUTO_POST_TITLE, null, List.of(job.getResultImageUrl())), userId);
        job.linkPetPost(post.id());
        return new FigurinePublishResponse(post.id());
    }

    private FigurineJob findOwnedJob(Long jobId, Long userId) {
        FigurineJob job = figurineJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FIGURINE_JOB_NOT_FOUND));
        if (!job.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return job;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.jipsamoye.backend.domain.figurine.service.FigurineServiceImplTest'`
Expected: PASS (9 tests)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/figurine src/test/java/com/jipsamoye/backend/domain/figurine src/main/java/com/jipsamoye/backend/global/code/ErrorCode.java
git commit -m "feat: figurine 서비스·DTO·에러코드 추가"
```

---

### Task 7: FigurineController + 전체 검증

SecurityConfig 수정 불필요 — `anyRequest().authenticated()`로 자동 인증 필수. 이 태스크의 테스트 게이트는 전체 `./gradlew test`(ArchUnit 레이어/DTO 규칙 포함)다.

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/domain/figurine/controller/FigurineController.java`

**Interfaces:**
- Consumes: `FigurineService` (Task 6), `ApiResponse.success/created`, `CustomUserDetails.getUserId()`
- Produces: `POST /api/figurines`(201), `GET /api/figurines/{jobId}`(200), `POST /api/figurines/{jobId}/post`(201)

- [ ] **Step 1: 컨트롤러 구현**

`src/main/java/com/jipsamoye/backend/domain/figurine/controller/FigurineController.java`:

```java
package com.jipsamoye.backend.domain.figurine.controller;

import com.jipsamoye.backend.domain.figurine.dto.request.FigurineJobCreateRequest;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurineJobResponse;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurinePublishResponse;
import com.jipsamoye.backend.domain.figurine.service.FigurineService;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Figurine", description = "AI 키캡 이미지 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/figurines")
@Validated
public class FigurineController {

    private final FigurineService figurineService;

    @Operation(summary = "AI 키캡 이미지 생성 요청", description = "업로드된 반려동물 사진으로 키캡 굿즈 이미지 생성 작업을 시작한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<FigurineJobResponse>> createJob(
            @Valid @RequestBody FigurineJobCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FigurineJobResponse response = figurineService.createJob(request, userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "생성 작업 상태 조회", description = "프론트가 2~3초 간격으로 폴링한다.")
    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<FigurineJobResponse>> getJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FigurineJobResponse response = figurineService.getJob(jobId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "생성 결과 자동 게시", description = "완성된 이미지를 제목 'AI 키캡 자랑'으로 자랑 피드에 게시한다.")
    @PostMapping("/{jobId}/post")
    public ResponseEntity<ApiResponse<FigurinePublishResponse>> publishJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FigurinePublishResponse response = figurineService.publishJob(jobId, userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
```

주의: `CustomUserDetails`의 실제 패키지는 구현 시 `import` 자동완성 대신 기존 컨트롤러(예: `src/main/java/com/jipsamoye/backend/domain/petPost/controller/PetPostController.java`)의 import를 확인해 맞춘다.

- [ ] **Step 2: 전체 테스트 + ArchUnit 통과 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — 기존 272개 + figurine 신규 테스트 전부 PASS, `ArchitectureTest` 포함

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/figurine/controller
git commit -m "feat: figurine API 컨트롤러 추가 (생성 요청·폴링·자동 게시)"
```

---

### Task 8: 문서 반영 + 최종 검증

**Files:**
- Modify: `docs/ARCHITECTURE.md` (도메인 맵 테이블, `boardLike` 행 아래)
- Modify: `docs/QUALITY.md` (품질 등급 테이블, `boardLike` 행 아래)

- [ ] **Step 1: ARCHITECTURE.md 도메인 맵에 행 추가**

`| \`boardLike\` | 자유게시판 좋아요 토글 | \`BoardLike\` |` 행 아래에 추가:

```markdown
| `figurine` | AI 키캡 이미지 생성(OpenAI gpt-image-1 비동기), 자랑 피드 자동 게시 | `FigurineJob` |
```

- [ ] **Step 2: QUALITY.md 품질 등급 테이블에 행 추가**

`| boardLike | B | 없음 | 자유게시판 좋아요 토글 |` 행 아래에 추가:

```markdown
| figurine | B | 있음 | AI 키캡 이미지 생성(OpenAI 비동기 + S3). 엔티티·클라이언트·스토리지·프로세서·서비스 단위 테스트 존재 |
```

- [ ] **Step 3: 최종 전체 검증**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add docs/ARCHITECTURE.md docs/QUALITY.md
git commit -m "docs: figurine 도메인 아키텍처·품질 문서 반영"
```

---

## 검증 (전체 완료 후)

1. `./gradlew build` — 전체 테스트(기존 272개 + 신규 ~32개) + ArchUnit 통과
2. 로컬 수동 검증 (`OPENAI_API_KEY` 환경변수 설정 시):
   - 로컬 부팅 → presigned 흐름으로 사진 업로드(dirName=posts) → `POST /api/figurines {sourceImageUrl}` → `GET /api/figurines/{jobId}` 폴링으로 COMPLETED 확인 → resultImageUrl 열어 키캡 스타일 확인 → `POST /api/figurines/{jobId}/post` → 자랑 피드에 "AI 키캡 자랑" 게시글 확인
   - 키 미설정 시에도 로컬 부팅은 가능해야 함 (`${OPENAI_API_KEY:}` 빈 기본값)
3. 브랜치 통합: `feature/figurine-ai` → `develop` 머지 (superpowers:finishing-a-development-branch 스킬 사용). develop→main PR은 운영 배포이므로 반드시 사용자 확인 후 진행 (CLAUDE.md)

## 알려진 트레이드오프 (스펙에서 사용자와 합의됨)

- `@Async` 메모리 큐: 재시작 시 진행 중 job 유실 → getJob의 5분 타임아웃 규칙 + 프론트 재시도로 방어
- 사용량 무제한: OpenAI 대시보드 월 지출 한도 설정 권장, 쿼터는 추후
- 미게시 결과·원본이 S3에 남음: 기존 프로젝트 전반의 알려진 제약과 동일, 별도 처리 안 함
