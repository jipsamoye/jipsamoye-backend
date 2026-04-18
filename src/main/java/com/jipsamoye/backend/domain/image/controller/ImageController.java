package com.jipsamoye.backend.domain.image.controller;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;
import com.jipsamoye.backend.domain.image.service.ImageService;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Image", description = "이미지 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "Presigned URL 발급", description = "S3 이미지 업로드용 Presigned URL을 발급합니다. 발급된 URL로 PUT 요청하여 이미지를 업로드하세요.")
    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> generatePresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PresignedUrlResponse response = imageService.generatePresignedUrl(request, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
