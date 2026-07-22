package com.jipsamoye.backend.domain.figurine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FigurineJobCreateRequest(
        @NotBlank @Size(max = 500) String sourceImageUrl
) {
}
