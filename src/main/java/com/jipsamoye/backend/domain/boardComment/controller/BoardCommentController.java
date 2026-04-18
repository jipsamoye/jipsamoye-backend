package com.jipsamoye.backend.domain.boardComment.controller;

import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentCreateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentUpdateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.response.BoardCommentResponse;
import com.jipsamoye.backend.domain.boardComment.service.BoardCommentService;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "BoardComment", description = "자유게시판 댓글 API")
@RestController
@RequiredArgsConstructor
public class BoardCommentController {

    private final BoardCommentService boardCommentService;

    @Operation(summary = "댓글 작성", description = "자유게시판 게시글에 댓글을 작성합니다.")
    @PostMapping("/api/boards/{boardId}/comments")
    public ResponseEntity<ApiResponse<BoardCommentResponse>> createComment(
            @Parameter(description = "게시글 ID") @PathVariable Long boardId,
            @Valid @RequestBody BoardCommentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        BoardCommentResponse response = boardCommentService.createComment(boardId, request, userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글 목록을 조회합니다.")
    @GetMapping("/api/boards/{boardId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<BoardCommentResponse>>> getComments(
            @Parameter(description = "게시글 ID") @PathVariable Long boardId,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<BoardCommentResponse> response = boardCommentService.getComments(boardId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "댓글 수정", description = "본인 댓글만 수정할 수 있습니다.")
    @PatchMapping("/api/board-comments/{id}")
    public ResponseEntity<ApiResponse<BoardCommentResponse>> updateComment(
            @Parameter(description = "댓글 ID") @PathVariable Long id,
            @Valid @RequestBody BoardCommentUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        BoardCommentResponse response = boardCommentService.updateComment(id, request, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("댓글 수정 성공", response));
    }

    @Operation(summary = "댓글 삭제", description = "본인 댓글만 삭제할 수 있습니다.")
    @DeleteMapping("/api/board-comments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @Parameter(description = "댓글 ID") @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardCommentService.deleteComment(id, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("댓글 삭제 성공"));
    }
}
