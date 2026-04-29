package com.jipsamoye.backend.domain.comment.service;

import com.jipsamoye.backend.domain.comment.dto.request.CommentCreateRequest;
import com.jipsamoye.backend.domain.comment.dto.request.CommentUpdateRequest;
import com.jipsamoye.backend.domain.comment.dto.response.CommentResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CommentService {

    CommentResponse create(CommentCreateRequest request, Long userId);

    CommentResponse update(Long commentId, CommentUpdateRequest request, Long userId);

    void delete(Long commentId, Long userId);

    PageResponse<CommentResponse> getCommentsByPost(Long postId, Pageable pageable);

    PageResponse<CommentResponse> getReplies(Long parentId, Pageable pageable);
}
