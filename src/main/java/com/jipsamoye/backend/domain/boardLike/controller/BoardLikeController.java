package com.jipsamoye.backend.domain.boardLike.controller;

import com.jipsamoye.backend.domain.boardLike.service.BoardLikeService;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BoardLike", description = "자유게시판 좋아요 API")
@RestController
@RequiredArgsConstructor
public class BoardLikeController {

    private final BoardLikeService boardLikeService;

    @Operation(summary = "좋아요 토글", description = "좋아요가 없으면 추가, 있으면 취소합니다.")
    @PostMapping("/api/boards/{boardId}/like")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(
            @Parameter(description = "게시글 ID") @PathVariable Long boardId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean liked = boardLikeService.toggleLike(boardId, userDetails.getUserId());
        String message = liked ? "좋아요 성공" : "좋아요 취소";
        return ResponseEntity.ok(ApiResponse.success(message, liked));
    }
}
