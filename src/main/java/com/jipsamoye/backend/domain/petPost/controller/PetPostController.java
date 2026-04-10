package com.jipsamoye.backend.domain.petPost.controller;

import com.jipsamoye.backend.domain.petPost.dto.request.PetPostCreateRequest;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostUpdateRequest;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostResponse;
import com.jipsamoye.backend.domain.petPost.service.PetPostService;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "PetPost", description = "게시글 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PetPostController {

    private final PetPostService petPostService;

    @Operation(summary = "게시글 작성", description = "새 게시글을 작성합니다. 이미지 1~5장 필수.")
    @PostMapping
    public ResponseEntity<ApiResponse<PetPostResponse>> createPost(
            @Valid @RequestBody PetPostCreateRequest request,
            @Parameter(description = "작성자 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        PetPostResponse response = petPostService.createPost(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PetPostResponse>> getPost(
            @Parameter(description = "게시글 ID") @PathVariable Long id) {
        PetPostResponse response = petPostService.getPost(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 목록 조회", description = "최신순으로 게시글 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<?>>> getPosts(
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<?> response = petPostService.getPosts(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 수정", description = "본인 게시글만 수정할 수 있습니다.")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PetPostResponse>> updatePost(
            @Parameter(description = "게시글 ID") @PathVariable Long id,
            @Valid @RequestBody PetPostUpdateRequest request,
            @Parameter(description = "작성자 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        PetPostResponse response = petPostService.updatePost(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("게시글 수정 성공", response));
    }

    @Operation(summary = "게시글 삭제", description = "본인 게시글만 삭제할 수 있습니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @Parameter(description = "게시글 ID") @PathVariable Long id,
            @Parameter(description = "작성자 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        petPostService.deletePost(id, userId);
        return ResponseEntity.ok(ApiResponse.success("게시글 삭제 성공"));
    }
}
