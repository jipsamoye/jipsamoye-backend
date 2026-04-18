package com.jipsamoye.backend.domain.board.service;

import com.jipsamoye.backend.domain.board.dto.request.BoardCreateRequest;
import com.jipsamoye.backend.domain.board.dto.request.BoardUpdateRequest;
import com.jipsamoye.backend.domain.board.dto.response.BoardListResponse;
import com.jipsamoye.backend.domain.board.dto.response.BoardResponse;
import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import com.jipsamoye.backend.global.response.PageResponse;

public interface BoardService {

    BoardResponse createBoard(BoardCreateRequest request, Long userId);

    BoardResponse getBoard(Long id);

    PageResponse<BoardListResponse> getBoards(BoardCategory category, int page, int size);

    BoardResponse updateBoard(Long id, BoardUpdateRequest request, Long userId);

    void deleteBoard(Long id, Long userId);

    PageResponse<BoardListResponse> searchBoards(String query, String type, int page, int size);
}
