package com.jipsamoye.backend.domain.like.controller;

import com.jipsamoye.backend.domain.like.service.LikeService;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Like", description = "좋아요 API")
@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "좋아요 토글", description = "좋아요가 없으면 추가, 있으면 취소합니다.")
    @PostMapping("/api/posts/{postId}/like")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(
            @Parameter(description = "게시글 ID") @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean liked = likeService.toggleLike(postId, userDetails.getUserId());
        String message = liked ? "좋아요 성공" : "좋아요 취소";
        return ResponseEntity.ok(ApiResponse.success(message, liked));
    }

    @Operation(summary = "좋아요한 게시글 목록", description = "내가 좋아요 누른 게시글 목록을 조회합니다.")
    @GetMapping("/api/users/me/likes")
    public ResponseEntity<ApiResponse<PageResponse<PetPostListResponse>>> getLikedPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<PetPostListResponse> response = likeService.getLikedPosts(userDetails.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
