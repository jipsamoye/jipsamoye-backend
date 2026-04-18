package com.jipsamoye.backend.domain.image.dto.response;

public record PresignedUrlResponse(
        String presignedUrl,
        String imageUrl
) {
}
