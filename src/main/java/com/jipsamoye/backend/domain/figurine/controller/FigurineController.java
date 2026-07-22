package com.jipsamoye.backend.domain.figurine.controller;

import com.jipsamoye.backend.domain.figurine.dto.request.FigurineJobCreateRequest;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurineJobResponse;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurinePublishResponse;
import com.jipsamoye.backend.domain.figurine.service.FigurineService;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Figurine", description = "AI 키캡 이미지 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/figurines")
@Validated
public class FigurineController {

    private final FigurineService figurineService;

    @Operation(summary = "AI 키캡 이미지 생성 요청", description = "업로드된 반려동물 사진으로 키캡 굿즈 이미지 생성 작업을 시작한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<FigurineJobResponse>> createJob(
            @Valid @RequestBody FigurineJobCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FigurineJobResponse response = figurineService.createJob(request, userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "생성 작업 상태 조회", description = "프론트가 2~3초 간격으로 폴링한다.")
    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<FigurineJobResponse>> getJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FigurineJobResponse response = figurineService.getJob(jobId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "생성 결과 자동 게시", description = "완성된 이미지를 제목 'AI 키캡 자랑'으로 자랑 피드에 게시한다.")
    @PostMapping("/{jobId}/post")
    public ResponseEntity<ApiResponse<FigurinePublishResponse>> publishJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FigurinePublishResponse response = figurineService.publishJob(jobId, userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
