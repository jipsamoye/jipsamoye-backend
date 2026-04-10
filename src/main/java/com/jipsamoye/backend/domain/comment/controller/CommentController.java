package com.jipsamoye.backend.domain.comment.controller;

import com.jipsamoye.backend.domain.comment.dto.request.CommentCreateRequest;
import com.jipsamoye.backend.domain.comment.dto.request.CommentUpdateRequest;
import com.jipsamoye.backend.domain.comment.dto.response.CommentResponse;
import com.jipsamoye.backend.domain.comment.service.CommentService;
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

@Tag(name = "Comment", description = "댓글 API")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다.")
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @Parameter(description = "게시글 ID") @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @Parameter(description = "작성자 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        CommentResponse response = commentService.createComment(postId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글 목록을 최신순으로 조회합니다.")
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getComments(
            @Parameter(description = "게시글 ID") @PathVariable Long postId,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<CommentResponse> response = commentService.getComments(postId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "댓글 수정", description = "본인 댓글만 수정할 수 있습니다.")
    @PatchMapping("/api/comments/{id}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @Parameter(description = "댓글 ID") @PathVariable Long id,
            @Valid @RequestBody CommentUpdateRequest request,
            @Parameter(description = "작성자 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        CommentResponse response = commentService.updateComment(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("댓글 수정 성공", response));
    }

    @Operation(summary = "댓글 삭제", description = "본인 댓글만 삭제할 수 있습니다.")
    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @Parameter(description = "댓글 ID") @PathVariable Long id,
            @Parameter(description = "작성자 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        commentService.deleteComment(id, userId);
        return ResponseEntity.ok(ApiResponse.success("댓글 삭제 성공"));
    }
}
