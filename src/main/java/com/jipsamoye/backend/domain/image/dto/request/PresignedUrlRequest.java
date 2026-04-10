package com.jipsamoye.backend.domain.image.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Presigned URL 발급 요청")
public class PresignedUrlRequest {

    @Schema(description = "이미지 용도 (posts 또는 profiles)", example = "posts")
    @NotBlank(message = "이미지 용도를 입력해주세요.")
    private String dirName;

    @Schema(description = "파일 확장자 (jpg, png, webp)", example = "jpg")
    @NotBlank(message = "파일 확장자를 입력해주세요.")
    private String ext;
}
