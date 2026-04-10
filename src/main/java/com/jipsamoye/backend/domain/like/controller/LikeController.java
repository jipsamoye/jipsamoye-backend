package com.jipsamoye.backend.domain.like.controller;

import com.jipsamoye.backend.domain.like.service.LikeService;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Like", description = "좋아요 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "좋아요 토글", description = "좋아요가 없으면 추가, 있으면 취소합니다.")
    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(
            @Parameter(description = "게시글 ID") @PathVariable Long postId,
            @Parameter(description = "유저 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        boolean liked = likeService.toggleLike(postId, userId);
        String message = liked ? "좋아요 성공" : "좋아요 취소";
        return ResponseEntity.ok(ApiResponse.success(message, liked));
    }
}
