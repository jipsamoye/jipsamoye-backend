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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "BoardComment", description = "자유게시판 댓글 API")
@RestController
@RequestMapping("/api/board-comments")
@RequiredArgsConstructor
@Validated
public class BoardCommentController {

    private final BoardCommentService boardCommentService;

    @Operation(summary = "댓글/답글 작성")
    @PostMapping
    public ResponseEntity<ApiResponse<BoardCommentResponse>> create(
            @Valid @RequestBody BoardCommentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        BoardCommentResponse response = boardCommentService.create(request, userDetails.getUserId());
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @Operation(summary = "댓글 수정")
    @PatchMapping("/{commentId}")
    public ResponseEntity<ApiResponse<BoardCommentResponse>> update(
            @Parameter(description = "댓글 ID") @PathVariable Long commentId,
            @Valid @RequestBody BoardCommentUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        BoardCommentResponse response = boardCommentService.update(commentId, request, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("댓글 수정 성공", response));
    }

    @Operation(summary = "댓글 삭제")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "댓글 ID") @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boardCommentService.delete(commentId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("댓글 삭제 성공"));
    }

    @Operation(summary = "게시글 댓글 목록 조회 (부모 + 답글 처음 3개)")
    @GetMapping("/board/{boardId}")
    public ResponseEntity<ApiResponse<PageResponse<BoardCommentResponse>>> getCommentsByBoard(
            @Parameter(description = "게시글 ID") @PathVariable Long boardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        PageResponse<BoardCommentResponse> response = boardCommentService.getCommentsByBoard(boardId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "답글 더보기")
    @GetMapping("/{parentId}/replies")
    public ResponseEntity<ApiResponse<PageResponse<BoardCommentResponse>>> getReplies(
            @Parameter(description = "부모 댓글 ID") @PathVariable Long parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        PageResponse<BoardCommentResponse> response = boardCommentService.getReplies(parentId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
