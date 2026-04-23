package com.jipsamoye.backend.domain.image.service;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${cdn.image-base-url:https://images.jipsamoye.com}")
    private String cdnBaseUrl;

    // 허용 확장자: jpg, jpeg, png, webp
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    // Presigned URL 만료 시간: 10분
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);
    // Lambda가 생성하는 썸네일 사이즈 — 삭제 시 원본과 함께 제거
    private static final List<Integer> THUMBNAIL_SIZES = List.of(200, 800);

    @Override
    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request, Long userId) {
        String ext = request.ext().toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "허용된 이미지 형식: jpg, png, webp");
        }

        String dirName = request.dirName();
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

        String imageUrl = cdnBaseUrl + "/" + key;

        return new PresignedUrlResponse(presignedRequest.url().toString(), imageUrl);
    }

    @Override
    public void deleteImage(String imageUrl) {
        deleteImages(List.of(imageUrl));
    }

    @Override
    public void deleteImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;

        List<ObjectIdentifier> keys = new ArrayList<>();
        for (String url : imageUrls) {
            String originalKey = extractKeyFromUrl(url);
            if (originalKey == null) continue;
            keys.addAll(toOriginalAndThumbnailKeys(originalKey));
        }
        if (keys.isEmpty()) return;

        // S3 DeleteObjects API — 원본 + 썸네일 2개를 한 호출에 배치 삭제
        // 존재하지 않는 key도 성공 응답 (idempotent) → 썸네일 미생성 케이스도 안전
        try {
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(keys).quiet(true).build())
                    .build());
        } catch (Exception e) {
            log.warn("S3 이미지 삭제 실패: {}", imageUrls, e);
        }
    }

    /**
     * 원본 key를 받아 [원본, thumb_200, thumb_800] key 목록으로 확장한다.
     * 예: posts/1/abc.webp → posts/1/abc.webp, posts/1/thumbnails/abc_200.webp, posts/1/thumbnails/abc_800.webp
     *
     * 경로 형식이 예상과 다르면 (디렉터리 또는 확장자 없음) 원본 key 한 개만 반환해 안전하게 축소한다.
     */
    private List<ObjectIdentifier> toOriginalAndThumbnailKeys(String originalKey) {
        int slashIdx = originalKey.lastIndexOf('/');
        int dotIdx = originalKey.lastIndexOf('.');
        if (slashIdx < 0 || dotIdx <= slashIdx) {
            return List.of(ObjectIdentifier.builder().key(originalKey).build());
        }

        String dir = originalKey.substring(0, slashIdx);
        String filename = originalKey.substring(slashIdx + 1, dotIdx);

        List<ObjectIdentifier> keys = new ArrayList<>();
        keys.add(ObjectIdentifier.builder().key(originalKey).build());
        for (Integer size : THUMBNAIL_SIZES) {
            keys.add(ObjectIdentifier.builder()
                    .key(dir + "/thumbnails/" + filename + "_" + size + ".webp")
                    .build());
        }
        return keys;
    }

    private String extractKeyFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
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
}
