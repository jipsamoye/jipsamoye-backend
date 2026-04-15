# Cloudflare 이미지 CDN 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** S3 이미지를 Cloudflare CDN을 경유하여 서빙하도록 백엔드를 변경한다.

**Architecture:** CDN base URL을 설정값으로 관리하고, `ImageCdnConverter` 유틸리티가 S3 URL → CDN URL 변환을 담당한다. 모든 Response DTO에서 이미지 URL 반환 시 컨버터를 통해 CDN URL로 변환한다. 기존 S3 URL과 새 CDN URL 모두 역변환(key 추출)을 지원한다.

**Tech Stack:** Spring Boot 3.5, Java 17, JUnit 5, Mockito

**Spec:** `docs/superpowers/specs/2026-04-16-cloudflare-image-cdn-design.md`

---

## 파일 구조

| 액션 | 파일 | 역할 |
|------|------|------|
| Create | `src/main/java/.../global/config/CdnProperties.java` | CDN 설정값 바인딩 |
| Create | `src/main/java/.../global/util/ImageCdnConverter.java` | S3 URL ↔ CDN URL 변환 |
| Create | `src/test/java/.../global/util/ImageCdnConverterTest.java` | 컨버터 단위 테스트 |
| Modify | `src/main/resources/application-prod.yaml` | CDN 설정값 추가 |
| Modify | `src/main/resources/application-local.yaml` | CDN 설정값 추가 (S3 URL) |
| Modify | `src/main/java/.../domain/image/service/ImageServiceImpl.java` | Presigned URL 응답에 CDN URL 사용 + 삭제 역변환 |
| Create | `src/test/java/.../domain/image/service/ImageServiceImplTest.java` | ImageService 단위 테스트 |
| Modify | `src/main/java/.../domain/petPost/dto/response/PetPostResponse.java` | imageUrls, profileImageUrl CDN 변환 |
| Modify | `src/main/java/.../domain/user/dto/response/UserResponse.java` | profileImageUrl, coverImageUrl CDN 변환 |
| Modify | `src/main/java/.../domain/comment/dto/response/CommentResponse.java` | profileImageUrl CDN 변환 |
| Modify | `src/main/java/.../domain/dm/dto/response/DmMessageResponse.java` | imageUrl CDN 변환 |
| Modify | `src/main/java/.../domain/follow/dto/response/FollowUserResponse.java` | profileImageUrl CDN 변환 |
| Modify | `src/main/java/.../domain/chat/dto/response/ChatMessageResponse.java` | profileImageUrl CDN 변환 |
| Modify | `.github/workflows/deploy.yml` | CDN_IMAGE_BASE_URL 환경변수 추가 |

> 경로 prefix: `com/jipsamoye/backend`

---

### Task 1: CDN 설정값 + 컨버터 테스트 작성

**Files:**
- Create: `src/test/java/com/jipsamoye/backend/global/util/ImageCdnConverterTest.java`

- [ ] **Step 1: 컨버터 테스트 작성**

```java
package com.jipsamoye.backend.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCdnConverterTest {

    private final ImageCdnConverter converter = new ImageCdnConverter(
            "https://images.jipsamoye.com",
            "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com"
    );

    @Test
    @DisplayName("S3 URL을 CDN URL로 변환한다")
    void toCdnUrl_convertsS3Url() {
        String s3Url = "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/uuid.jpg";
        String result = converter.toCdnUrl(s3Url);
        assertThat(result).isEqualTo("https://images.jipsamoye.com/posts/1/uuid.jpg");
    }

    @Test
    @DisplayName("이미 CDN URL이면 그대로 반환한다")
    void toCdnUrl_alreadyCdnUrl() {
        String cdnUrl = "https://images.jipsamoye.com/posts/1/uuid.jpg";
        String result = converter.toCdnUrl(cdnUrl);
        assertThat(result).isEqualTo("https://images.jipsamoye.com/posts/1/uuid.jpg");
    }

    @Test
    @DisplayName("null이면 null을 반환한다")
    void toCdnUrl_null() {
        assertThat(converter.toCdnUrl(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열이면 빈 문자열을 반환한다")
    void toCdnUrl_empty() {
        assertThat(converter.toCdnUrl("")).isEmpty();
    }

    @Test
    @DisplayName("URL 리스트를 CDN URL 리스트로 변환한다")
    void toCdnUrls_convertsList() {
        List<String> urls = List.of(
                "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/a.jpg",
                "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/b.webp"
        );
        List<String> result = converter.toCdnUrls(urls);
        assertThat(result).containsExactly(
                "https://images.jipsamoye.com/posts/1/a.jpg",
                "https://images.jipsamoye.com/posts/1/b.webp"
        );
    }

    @Test
    @DisplayName("null 리스트이면 빈 리스트를 반환한다")
    void toCdnUrls_nullList() {
        assertThat(converter.toCdnUrls(null)).isEmpty();
    }

    @Test
    @DisplayName("CDN URL에서 S3 key를 추출한다")
    void extractKey_fromCdnUrl() {
        String cdnUrl = "https://images.jipsamoye.com/posts/1/uuid.jpg";
        String key = converter.extractKey(cdnUrl);
        assertThat(key).isEqualTo("posts/1/uuid.jpg");
    }

    @Test
    @DisplayName("S3 URL에서 S3 key를 추출한다")
    void extractKey_fromS3Url() {
        String s3Url = "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/uuid.jpg";
        String key = converter.extractKey(s3Url);
        assertThat(key).isEqualTo("posts/1/uuid.jpg");
    }

    @Test
    @DisplayName("null URL에서 key 추출 시 null을 반환한다")
    void extractKey_null() {
        assertThat(converter.extractKey(null)).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.jipsamoye.backend.global.util.ImageCdnConverterTest" --info`
Expected: FAIL — `ImageCdnConverter` 클래스가 존재하지 않아 컴파일 에러

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/jipsamoye/backend/global/util/ImageCdnConverterTest.java
git commit -m "test: ImageCdnConverter 단위 테스트 작성"
```

---

### Task 2: CDN 설정값 + 컨버터 구현

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/global/config/CdnProperties.java`
- Create: `src/main/java/com/jipsamoye/backend/global/util/ImageCdnConverter.java`
- Modify: `src/main/resources/application-prod.yaml`
- Modify: `src/main/resources/application-local.yaml`

- [ ] **Step 1: CdnProperties 생성**

```java
package com.jipsamoye.backend.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "cdn")
public class CdnProperties {

    private final String imageBaseUrl;
}
```

- [ ] **Step 2: ImageCdnConverter 생성**

```java
package com.jipsamoye.backend.global.util;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ImageCdnConverter {

    private final String cdnBaseUrl;
    private final String s3BaseUrl;

    public ImageCdnConverter(
            @org.springframework.beans.factory.annotation.Value("${cdn.image-base-url}") String cdnBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${cloud.aws.s3.bucket}") String bucket
    ) {
        this.cdnBaseUrl = cdnBaseUrl;
        String region = "ap-northeast-2";
        this.s3BaseUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }

    // 테스트용 생성자
    ImageCdnConverter(String cdnBaseUrl, String s3BaseUrl) {
        this.cdnBaseUrl = cdnBaseUrl;
        this.s3BaseUrl = s3BaseUrl;
    }

    public String toCdnUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;
        if (imageUrl.startsWith(s3BaseUrl)) {
            return cdnBaseUrl + imageUrl.substring(s3BaseUrl.length());
        }
        return imageUrl;
    }

    public List<String> toCdnUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return Collections.emptyList();
        return imageUrls.stream().map(this::toCdnUrl).toList();
    }

    public String extractKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        if (imageUrl.startsWith(cdnBaseUrl)) {
            return imageUrl.substring(cdnBaseUrl.length() + 1); // +1 for "/"
        }
        if (imageUrl.startsWith(s3BaseUrl)) {
            return imageUrl.substring(s3BaseUrl.length() + 1);
        }
        return null;
    }
}
```

- [ ] **Step 3: application-prod.yaml에 CDN 설정 추가**

기존 내용 끝에 추가:
```yaml
cdn:
  image-base-url: ${CDN_IMAGE_BASE_URL}
```

- [ ] **Step 4: application-local.yaml에 CDN 설정 추가**

기존 내용 끝에 추가:
```yaml
cdn:
  image-base-url: https://${AWS_S3_BUCKET}.s3.ap-northeast-2.amazonaws.com
```

- [ ] **Step 5: Application 클래스에 ConfigurationProperties 스캔 활성화**

`BackendApplication.java`에 `@ConfigurationPropertiesScan` 어노테이션 추가 (이미 있으면 스킵).

- [ ] **Step 6: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.jipsamoye.backend.global.util.ImageCdnConverterTest" --info`
Expected: PASS — 9개 테스트 모두 통과

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/global/config/CdnProperties.java \
        src/main/java/com/jipsamoye/backend/global/util/ImageCdnConverter.java \
        src/main/resources/application-prod.yaml \
        src/main/resources/application-local.yaml
git commit -m "feat: CDN 설정값 + ImageCdnConverter 구현"
```

---

### Task 3: ImageServiceImpl 변경 테스트 작성

**Files:**
- Create: `src/test/java/com/jipsamoye/backend/domain/image/service/ImageServiceImplTest.java`

- [ ] **Step 1: ImageServiceImpl 테스트 작성**

```java
package com.jipsamoye.backend.domain.image.service;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceImplTest {

    @InjectMocks
    private ImageServiceImpl imageService;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private ImageCdnConverter imageCdnConverter;

    @Test
    @DisplayName("Presigned URL 응답의 imageUrl이 CDN URL이다")
    void generatePresignedUrl_returnsCdnImageUrl() throws Exception {
        // given
        PresignedUrlRequest request = new PresignedUrlRequest("posts", "webp");

        PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
        when(mockPresigned.url()).thenReturn(URI.create("https://bucket.s3.amazonaws.com/presigned").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

        when(imageCdnConverter.toCdnUrl(any(String.class)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    return url.replace(
                            "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com",
                            "https://images.jipsamoye.com"
                    );
                });

        // when
        PresignedUrlResponse response = imageService.generatePresignedUrl(request, 1L);

        // then
        assertThat(response.getImageUrl()).startsWith("https://images.jipsamoye.com/");
        assertThat(response.getPresignedUrl()).contains("presigned");
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.jipsamoye.backend.domain.image.service.ImageServiceImplTest" --info`
Expected: FAIL — `ImageServiceImpl`에 `ImageCdnConverter` 의존성이 없어서 실패

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/jipsamoye/backend/domain/image/service/ImageServiceImplTest.java
git commit -m "test: ImageServiceImpl CDN URL 반환 테스트 작성"
```

---

### Task 4: ImageServiceImpl에 CDN 컨버터 적용

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/image/service/ImageServiceImpl.java:26-117`

- [ ] **Step 1: ImageServiceImpl 수정**

변경 사항:
1. `ImageCdnConverter` 의존성 추가
2. `generatePresignedUrl()`에서 `imageUrl`을 컨버터로 CDN URL 변환
3. `extractKeyFromUrl()`을 컨버터의 `extractKey()`로 교체

```java
package com.jipsamoye.backend.domain.image.service;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final ImageCdnConverter imageCdnConverter;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    @Override
    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request, Long userId) {
        String ext = request.getExt().toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "허용된 이미지 형식: jpg, png, webp");
        }

        String dirName = request.getDirName();
        if (!dirName.equals("posts") && !dirName.equals("profiles") && !dirName.equals("covers") && !dirName.equals("dm")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "dirName은 posts, profiles, covers, dm만 가능합니다.");
        }

        String key = dirName + "/" + userId + "/" + UUID.randomUUID() + "." + ext;

        String contentType = switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        String s3Url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);

        return PresignedUrlResponse.builder()
                .presignedUrl(presignedRequest.url().toString())
                .imageUrl(imageCdnConverter.toCdnUrl(s3Url))
                .build();
    }

    @Override
    public void deleteImage(String imageUrl) {
        String key = imageCdnConverter.extractKey(imageUrl);
        if (key == null) return;

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("S3 이미지 삭제 실패: {}", imageUrl, e);
        }
    }

    @Override
    public void deleteImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        imageUrls.forEach(this::deleteImage);
    }
}
```

- [ ] **Step 2: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.jipsamoye.backend.domain.image.service.ImageServiceImplTest" --info`
Expected: PASS

- [ ] **Step 3: 컨버터 테스트도 재확인**

Run: `./gradlew test --tests "com.jipsamoye.backend.global.util.ImageCdnConverterTest" --info`
Expected: PASS

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/image/service/ImageServiceImpl.java
git commit -m "feat: ImageServiceImpl에 CDN URL 변환 적용"
```

---

### Task 5: Response DTO에 CDN 컨버터 적용

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/petPost/dto/response/PetPostResponse.java:26-40`
- Modify: `src/main/java/com/jipsamoye/backend/domain/user/dto/response/UserResponse.java:26-39`
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/dto/response/CommentResponse.java:22-33`
- Modify: `src/main/java/com/jipsamoye/backend/domain/dm/dto/response/DmMessageResponse.java:21-31`
- Modify: `src/main/java/com/jipsamoye/backend/domain/follow/dto/response/FollowUserResponse.java:15-21`
- Modify: `src/main/java/com/jipsamoye/backend/domain/chat/dto/response/ChatMessageResponse.java:20-30`

각 Response DTO의 `from()` / `of()` 정적 팩토리 메서드에 `ImageCdnConverter` 파라미터를 추가한다.

- [ ] **Step 1: PetPostResponse 수정**

```java
// 기존 import에 추가
import com.jipsamoye.backend.global.util.ImageCdnConverter;

// from 메서드 시그니처 변경
public static PetPostResponse from(PetPost petPost, ImageCdnConverter converter) {
    boolean isUserDeleted = petPost.getUser().isDeleted();
    return PetPostResponse.builder()
            .id(petPost.getId())
            .title(petPost.getTitle())
            .content(petPost.getContent())
            .imageUrls(converter.toCdnUrls(petPost.getImageUrls()))
            .likeCount(petPost.getLikeCount())
            .userId(petPost.getUser().getId())
            .nickname(isUserDeleted ? "탈퇴한 사용자" : petPost.getUser().getNickname())
            .profileImageUrl(isUserDeleted ? null : converter.toCdnUrl(petPost.getUser().getProfileImageUrl()))
            .createdAt(petPost.getCreatedAt())
            .updatedAt(petPost.getUpdatedAt())
            .build();
}
```

- [ ] **Step 2: UserResponse 수정**

```java
import com.jipsamoye.backend.global.util.ImageCdnConverter;

public static UserResponse of(User user, long postCount, long followerCount, long followingCount, ImageCdnConverter converter) {
    return UserResponse.builder()
            .id(user.getId())
            .nickname(user.getNickname())
            .bio(user.getBio())
            .profileImageUrl(converter.toCdnUrl(user.getProfileImageUrl()))
            .coverImageUrl(converter.toCdnUrl(user.getCoverImageUrl()))
            .socialLinks(user.getSocialLinks())
            .postCount(postCount)
            .followerCount(followerCount)
            .followingCount(followingCount)
            .createdAt(user.getCreatedAt())
            .build();
}
```

- [ ] **Step 3: CommentResponse 수정**

```java
import com.jipsamoye.backend.global.util.ImageCdnConverter;

public static CommentResponse from(Comment comment, ImageCdnConverter converter) {
    boolean isUserDeleted = comment.getUser().isDeleted();
    return CommentResponse.builder()
            .id(comment.getId())
            .content(comment.getContent())
            .userId(comment.getUser().getId())
            .nickname(isUserDeleted ? "탈퇴한 사용자" : comment.getUser().getNickname())
            .profileImageUrl(isUserDeleted ? null : converter.toCdnUrl(comment.getUser().getProfileImageUrl()))
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .build();
}
```

- [ ] **Step 4: DmMessageResponse 수정**

```java
import com.jipsamoye.backend.global.util.ImageCdnConverter;

public static DmMessageResponse from(DmMessage message, ImageCdnConverter converter) {
    return DmMessageResponse.builder()
            .id(message.getId())
            .senderId(message.getSender().getId())
            .senderNickname(message.getSender().getNickname())
            .content(message.getContent())
            .imageUrl(converter.toCdnUrl(message.getImageUrl()))
            .readAt(message.getReadAt())
            .createdAt(message.getCreatedAt())
            .build();
}
```

- [ ] **Step 5: FollowUserResponse 수정**

```java
import com.jipsamoye.backend.global.util.ImageCdnConverter;

public static FollowUserResponse from(User user, ImageCdnConverter converter) {
    return FollowUserResponse.builder()
            .id(user.getId())
            .nickname(user.getNickname())
            .profileImageUrl(converter.toCdnUrl(user.getProfileImageUrl()))
            .build();
}
```

- [ ] **Step 6: ChatMessageResponse 수정**

```java
import com.jipsamoye.backend.global.util.ImageCdnConverter;

public static ChatMessageResponse from(ChatMessage message, ImageCdnConverter converter) {
    return ChatMessageResponse.builder()
            .type("CHAT_MESSAGE")
            .id(message.getId())
            .userId(message.getSender().getId())
            .nickname(message.getSender().getNickname())
            .profileImageUrl(converter.toCdnUrl(message.getSender().getProfileImageUrl()))
            .content(message.getContent())
            .createdAt(message.getCreatedAt())
            .build();
}
```

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/domain/petPost/dto/response/PetPostResponse.java \
        src/main/java/com/jipsamoye/backend/domain/user/dto/response/UserResponse.java \
        src/main/java/com/jipsamoye/backend/domain/comment/dto/response/CommentResponse.java \
        src/main/java/com/jipsamoye/backend/domain/dm/dto/response/DmMessageResponse.java \
        src/main/java/com/jipsamoye/backend/domain/follow/dto/response/FollowUserResponse.java \
        src/main/java/com/jipsamoye/backend/domain/chat/dto/response/ChatMessageResponse.java
git commit -m "feat: Response DTO에 CDN URL 변환 적용"
```

---

### Task 6: Service 호출부 수정

Response DTO의 `from()` / `of()` 시그니처가 변경되었으므로, 이를 호출하는 Service 클래스들을 수정한다. 각 Service에 `ImageCdnConverter`를 주입하고, Response 생성 시 컨버터를 전달한다.

**Files:**
- Modify: `src/main/java/com/jipsamoye/backend/domain/petPost/service/PetPostServiceImpl.java`
- Modify: `src/main/java/com/jipsamoye/backend/domain/user/service/UserServiceImpl.java` (또는 UserResponse를 생성하는 Service)
- Modify: `src/main/java/com/jipsamoye/backend/domain/comment/service/CommentServiceImpl.java` (또는 CommentResponse를 생성하는 Service)
- Modify: `src/main/java/com/jipsamoye/backend/domain/dm/service/DmServiceImpl.java`
- Modify: `src/main/java/com/jipsamoye/backend/domain/follow/service/FollowServiceImpl.java` (또는 FollowUserResponse를 생성하는 Service)
- Modify: `src/main/java/com/jipsamoye/backend/domain/chat/service/ChatServiceImpl.java` (또는 ChatMessageResponse를 생성하는 Service)

각 Service에 동일한 패턴 적용:

1. `ImageCdnConverter` 필드 추가 (생성자 주입 — `@RequiredArgsConstructor`)
2. `Response.from(entity)` 호출을 `Response.from(entity, imageCdnConverter)` 로 변경

- [ ] **Step 1: 각 Service에서 `XxxResponse.from(entity)` 호출부를 찾아 `imageCdnConverter` 파라미터 추가**

변경 패턴 (모든 Service 동일):
```java
// 필드 추가 (기존 final 필드들과 함께)
private final ImageCdnConverter imageCdnConverter;

// 기존
PetPostResponse.from(petPost)
// 변경
PetPostResponse.from(petPost, imageCdnConverter)
```

검색해야 할 호출 패턴:
- `PetPostResponse.from(` — PetPostServiceImpl
- `UserResponse.of(` — UserServiceImpl
- `CommentResponse.from(` — CommentServiceImpl
- `DmMessageResponse.from(` — DmServiceImpl
- `FollowUserResponse.from(` — FollowServiceImpl
- `ChatMessageResponse.from(` — ChatServiceImpl 또는 ChatMessageListener

> 실행 시점에 grep으로 모든 호출부를 확인하고 빠짐없이 수정할 것.

- [ ] **Step 2: 빌드하여 컴파일 에러 없는지 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 전체 테스트 실행**

Run: `./gradlew test --info`
Expected: 기존 테스트 + 새 테스트 모두 PASS

- [ ] **Step 4: 커밋**

```bash
git add -A
git commit -m "feat: Service 계층에 ImageCdnConverter 주입 및 호출부 수정"
```

---

### Task 7: GitHub Actions 환경변수 추가

**Files:**
- Modify: `.github/workflows/deploy.yml:46-50`

- [ ] **Step 1: deploy.yml에 CDN_IMAGE_BASE_URL 환경변수 추가**

`.env` 파일 생성 부분에 한 줄 추가:
```yaml
            echo "CDN_IMAGE_BASE_URL=${{ secrets.CDN_IMAGE_BASE_URL }}" >> .env
```

기존 `echo` 블록 마지막에 추가한다 (`DISCORD_WEBHOOK_URL` 다음 줄).

- [ ] **Step 2: 커밋**

```bash
git add .github/workflows/deploy.yml
git commit -m "feat: deploy.yml에 CDN_IMAGE_BASE_URL 환경변수 추가"
```

- [ ] **Step 3: GitHub Secrets에 추가 (수동)**

GitHub 리포지토리 Settings → Secrets and variables → Actions에서:
- Name: `CDN_IMAGE_BASE_URL`
- Value: `https://images.jipsamoye.com`

---

### Task 8: 전체 빌드 + 테스트 최종 검증

- [ ] **Step 1: 전체 빌드**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 전체 테스트**

Run: `./gradlew test --info`
Expected: ALL PASS

- [ ] **Step 3: 로컬에서 서버 실행하여 이미지 URL 확인 (수동)**

1. 로컬 서버 실행
2. 게시글 조회 API 호출
3. 응답의 imageUrl이 CDN 도메인인지 확인 (로컬은 S3 도메인 그대로)

---

### Task 9: Cloudflare 설정 (수동 — 인프라)

이 Task는 코드가 아닌 인프라 설정이다. Cloudflare 대시보드에서 수동으로 진행한다.

- [ ] **Step 1: Cloudflare DNS에 서브도메인 추가**

Cloudflare Dashboard → jipsamoye.com → DNS에서:
- Type: `CNAME`
- Name: `images`
- Target: `jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com`
- Proxy status: **Proxied** (주황색 구름 ON)

- [ ] **Step 2: 이미지 접근 확인**

브라우저에서 기존 S3 이미지의 CDN URL 버전에 접근:
```
https://images.jipsamoye.com/posts/{userId}/{uuid}.jpg
```
이미지가 정상적으로 보이는지 확인.

- [ ] **Step 3: Cloudflare Cache Rules 설정**

Cloudflare Dashboard → jipsamoye.com → Caching → Cache Rules에서:
- Rule name: `Image CDN Cache`
- When: hostname equals `images.jipsamoye.com`
- Then: Cache eligible, Edge TTL override → 1 year

- [ ] **Step 4: 캐시 동작 확인**

같은 이미지를 두 번 요청하고, 브라우저 개발자 도구 → Network에서:
- 응답 헤더에 `cf-cache-status: HIT` 가 있으면 캐싱 정상 동작
