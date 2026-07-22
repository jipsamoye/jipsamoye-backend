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
