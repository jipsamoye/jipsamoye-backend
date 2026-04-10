package com.jipsamoye.backend.domain.comment.service;

import com.jipsamoye.backend.domain.comment.dto.request.CommentCreateRequest;
import com.jipsamoye.backend.domain.comment.dto.request.CommentUpdateRequest;
import com.jipsamoye.backend.domain.comment.dto.response.CommentResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface CommentService {

    CommentResponse createComment(Long postId, CommentCreateRequest request, Long userId);

    PageResponse<CommentResponse> getComments(Long postId, int page, int size);

    CommentResponse updateComment(Long commentId, CommentUpdateRequest request, Long userId);

    void deleteComment(Long commentId, Long userId);
}
