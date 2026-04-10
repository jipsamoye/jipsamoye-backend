package com.jipsamoye.backend.domain.image.service;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final S3Presigner s3Presigner;

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
}
