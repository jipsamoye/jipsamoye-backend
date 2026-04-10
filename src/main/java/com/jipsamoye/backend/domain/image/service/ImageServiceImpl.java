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

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    // 허용 확장자: jpg, jpeg, png, webp
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    // Presigned URL 만료 시간: 10분
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    @Override
    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request, Long userId) {
        String ext = request.getExt().toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "허용된 이미지 형식: jpg, png, webp");
        }

        String dirName = request.getDirName();
        if (!dirName.equals("posts") && !dirName.equals("profiles")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "dirName은 posts 또는 profiles만 가능합니다.");
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

        String imageUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);

        return PresignedUrlResponse.builder()
                .presignedUrl(presignedRequest.url().toString())
                .imageUrl(imageUrl)
                .build();
    }

    @Override
    public void deleteImage(String imageUrl) {
        String key = extractKeyFromUrl(imageUrl);
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

    /**
     * imageUrl에서 S3 key 추출
     * https://bucket.s3.region.amazonaws.com/posts/1/uuid.jpg → posts/1/uuid.jpg
     */
    private String extractKeyFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String prefix = String.format("https://%s.s3.%s.amazonaws.com/", bucket, region);
        if (imageUrl.startsWith(prefix)) {
            return imageUrl.substring(prefix.length());
        }
        return null;
    }
}
