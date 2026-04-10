package com.jipsamoye.backend.domain.comment.service;

import com.jipsamoye.backend.domain.comment.dto.request.CommentCreateRequest;
import com.jipsamoye.backend.domain.comment.dto.request.CommentUpdateRequest;
import com.jipsamoye.backend.domain.comment.dto.response.CommentResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface CommentService {

    CommentResponse createComment(Long postId, CommentCreateRequest request, Long userId);

    PageResponse<CommentResponse> getComments(Long postId, int page, int size);

    CommentResponse updateComment(Long commentId, CommentUpdateRequest request, Long userId);

    /**
     * 댓글 삭제 — soft delete, 본인 댓글만 가능
     */
    void deleteComment(Long commentId, Long userId);
}
