package com.jipsamoye.backend.domain.boardComment.service;

import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentCreateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentUpdateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.response.BoardCommentResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface BoardCommentService {

    BoardCommentResponse create(BoardCommentCreateRequest request, Long userId);

    BoardCommentResponse update(Long commentId, BoardCommentUpdateRequest request, Long userId);

    void delete(Long commentId, Long userId);

    PageResponse<BoardCommentResponse> getCommentsByBoard(Long boardId, Pageable pageable);

    PageResponse<BoardCommentResponse> getReplies(Long parentId, Pageable pageable);
}
