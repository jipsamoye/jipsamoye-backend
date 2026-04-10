package com.jipsamoye.backend.domain.image.service;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;

import java.util.List;

public interface ImageService {

    /**
     * S3 Presigned URL 발급 — PUT 업로드용, 10분 만료
     * 허용: jpg/png/webp, 디렉토리: posts 또는 profiles
     */
    PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request, Long userId);

    /**
     * S3 이미지 삭제 — imageUrl에서 S3 key를 추출하여 삭제
     */
    void deleteImage(String imageUrl);

    /**
     * S3 이미지 여러 장 삭제
     */
    void deleteImages(List<String> imageUrls);
}
