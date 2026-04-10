package com.jipsamoye.backend.domain.image.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PresignedUrlResponse {

    private String presignedUrl;
    private String imageUrl;
}
