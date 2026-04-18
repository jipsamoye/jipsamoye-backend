package com.jipsamoye.backend.domain.boardComment.service;

import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentCreateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentUpdateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.response.BoardCommentResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface BoardCommentService {

    BoardCommentResponse createComment(Long boardId, BoardCommentCreateRequest request, Long userId);

    PageResponse<BoardCommentResponse> getComments(Long boardId, int page, int size);

    BoardCommentResponse updateComment(Long commentId, BoardCommentUpdateRequest request, Long userId);

    void deleteComment(Long commentId, Long userId);
}
