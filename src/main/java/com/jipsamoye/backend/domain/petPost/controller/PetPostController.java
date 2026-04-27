package com.jipsamoye.backend.domain.petPost.controller;

import com.jipsamoye.backend.domain.petPost.dto.request.PetPostCreateRequest;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostUpdateRequest;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostResponse;
import com.jipsamoye.backend.domain.petPost.dto.response.RankingPageResponse;
import com.jipsamoye.backend.domain.petPost.entity.RankingPeriod;
import com.jipsamoye.backend.domain.petPost.service.PetPostService;
import com.jipsamoye.backend.domain.petPost.service.RankingService;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "PetPost", description = "게시글 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
@Validated
public class PetPostController {

    private final PetPostService petPostService;
    private final RankingService rankingService;

    @Operation(summary = "기간별 랭킹", description = "주간/월간 좋아요 랭킹을 조회합니다. date가 속한 주(월요일~일요일) 또는 월(1일~말일) 기준.")
    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<RankingPageResponse>> getRanking(
            @Parameter(description = "기간 구분 (WEEKLY | MONTHLY)", required = true)
            @RequestParam RankingPeriod period,
            @Parameter(description = "기준 날짜 (YYYY-MM-DD). 미지정 시 오늘")
            @RequestParam(required = false) LocalDate date,
            @Parameter(description = "페이지 번호 (0부터)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지 크기 (1~50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        RankingPageResponse response = rankingService.getRanking(period, date, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "오늘의 멍냥", description = "최근 24시간 내 좋아요 수 상위 게시글을 조회합니다. 1시간 단위 갱신.")
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<java.util.List<PetPostListResponse>>> getPopularPosts() {
        return ResponseEntity.ok(ApiResponse.success(petPostService.getPopularPosts()));
    }

    @Operation(summary = "좋아요 TOP 10", description = "좋아요 수 기준 상위 10개 게시글을 조회합니다.")
    @GetMapping("/top10")
    public ResponseEntity<ApiResponse<java.util.List<PetPostListResponse>>> getTop10Posts() {
        return ResponseEntity.ok(ApiResponse.success(petPostService.getTop10Posts()));
    }

    @Operation(summary = "게시글 작성", description = "새 게시글을 작성합니다. 이미지 1~5장 필수.")
    @PostMapping
    public ResponseEntity<ApiResponse<PetPostResponse>> createPost(
            @Valid @RequestBody PetPostCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PetPostResponse response = petPostService.createPost(request, userDetails.getUserId());
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
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PetPostResponse response = petPostService.updatePost(id, request, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("게시글 수정 성공", response));
    }

    @Operation(summary = "게시글 삭제", description = "본인 게시글만 삭제할 수 있습니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @Parameter(description = "게시글 ID") @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        petPostService.deletePost(id, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("게시글 삭제 성공"));
    }

    @Operation(summary = "게시글 검색", description = "제목 기반으로 게시글을 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<?>>> searchPosts(
            @Parameter(description = "검색 키워드") @RequestParam String q,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<?> response = petPostService.searchPosts(q, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
