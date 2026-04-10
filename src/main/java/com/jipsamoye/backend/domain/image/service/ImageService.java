package com.jipsamoye.backend.domain.image.service;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;

public interface ImageService {

    /**
     * S3 Presigned URL 발급 — PUT 업로드용, 10분 만료
     * 허용: jpg/png/webp, 디렉토리: posts 또는 profiles
     */
    PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request, Long userId);
}
