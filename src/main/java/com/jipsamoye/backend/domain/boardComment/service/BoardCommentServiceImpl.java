package com.jipsamoye.backend.domain.boardComment.service;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.repository.BoardRepository;
import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentCreateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentUpdateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.response.BoardCommentResponse;
import com.jipsamoye.backend.domain.boardComment.entity.BoardComment;
import com.jipsamoye.backend.domain.boardComment.repository.BoardCommentRepository;
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
public class BoardCommentServiceImpl implements BoardCommentService {

    private final BoardCommentRepository boardCommentRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public BoardCommentResponse createComment(Long boardId, BoardCommentCreateRequest request, Long userId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        BoardComment comment = BoardComment.builder()
                .board(board)
                .user(user)
                .content(request.content())
                .build();

        BoardComment saved = boardCommentRepository.save(comment);
        boardRepository.updateCommentCount(boardId, 1);
        return BoardCommentResponse.from(saved);
    }

    @Override
    public PageResponse<BoardCommentResponse> getComments(Long boardId, int page, int size) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        Page<BoardCommentResponse> commentPage = boardCommentRepository
                .findAllByBoardOrderByCreatedAtDesc(board, PageRequest.of(page, size))
                .map(BoardCommentResponse::from);

        return PageResponse.from(commentPage);
    }

    @Override
    @Transactional
    public BoardCommentResponse updateComment(Long commentId, BoardCommentUpdateRequest request, Long userId) {
        BoardComment comment = boardCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        comment.update(request.content());
        return BoardCommentResponse.from(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        BoardComment comment = boardCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long boardId = comment.getBoard().getId();
        comment.softDelete();
        boardRepository.updateCommentCount(boardId, -1);
    }
}
