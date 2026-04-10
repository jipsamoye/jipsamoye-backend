package com.jipsamoye.backend.domain.image.service;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;

public interface ImageService {

    PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request, Long userId);
}
