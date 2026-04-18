package com.jipsamoye.backend.domain.board.service;

import com.jipsamoye.backend.domain.board.dto.request.BoardCreateRequest;
import com.jipsamoye.backend.domain.board.dto.request.BoardUpdateRequest;
import com.jipsamoye.backend.domain.board.dto.response.BoardListResponse;
import com.jipsamoye.backend.domain.board.dto.response.BoardResponse;
import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import com.jipsamoye.backend.domain.board.repository.BoardRepository;
import com.jipsamoye.backend.domain.boardComment.repository.BoardCommentRepository;
import com.jipsamoye.backend.domain.boardLike.repository.BoardLikeRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardServiceImpl implements BoardService {

    private final BoardRepository boardRepository;
    private final BoardCommentRepository boardCommentRepository;
    private final BoardLikeRepository boardLikeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public BoardResponse createBoard(BoardCreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Board board = Board.builder()
                .user(user)
                .category(request.category())
                .title(request.title())
                .content(request.content())
                .imageUrls(request.imageUrls())
                .build();

        return BoardResponse.from(boardRepository.save(board));
    }

    @Override
    @Transactional
    public BoardResponse getBoard(Long id) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        board.increaseViewCount();
        return BoardResponse.from(board);
    }

    @Override
    public PageResponse<BoardListResponse> getBoards(BoardCategory category, int page, int size) {
        Page<BoardListResponse> boardPage;
        PageRequest pageRequest = PageRequest.of(page, size);

        if (category == null) {
            boardPage = boardRepository.findAllByOrderByCreatedAtDesc(pageRequest)
                    .map(BoardListResponse::from);
        } else {
            boardPage = boardRepository.findAllByCategoryOrderByCreatedAtDesc(category, pageRequest)
                    .map(BoardListResponse::from);
        }

        return PageResponse.from(boardPage);
    }

    @Override
    @Transactional
    public BoardResponse updateBoard(Long id, BoardUpdateRequest request, Long userId) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        board.update(request.title(), request.content(), request.imageUrls(), request.category());
        return BoardResponse.from(board);
    }

    @Override
    @Transactional
    public void deleteBoard(Long id, Long userId) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        boardLikeRepository.deleteAllByBoard(board);
        boardCommentRepository.softDeleteAllByBoard(board);
        board.softDelete();
    }

    @Override
    public PageResponse<BoardListResponse> searchBoards(String query, String type, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<BoardListResponse> boardPage;

        if ("TITLE".equals(type)) {
            boardPage = boardRepository.findByTitleContaining(query, pageRequest)
                    .map(BoardListResponse::from);
        } else {
            boardPage = boardRepository.findByTitleContainingOrContentContaining(query, query, pageRequest)
                    .map(BoardListResponse::from);
        }

        return PageResponse.from(boardPage);
    }
}
