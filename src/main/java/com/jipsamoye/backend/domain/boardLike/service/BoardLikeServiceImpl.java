package com.jipsamoye.backend.domain.boardLike.service;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.repository.BoardRepository;
import com.jipsamoye.backend.domain.boardLike.entity.BoardLike;
import com.jipsamoye.backend.domain.boardLike.repository.BoardLikeRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardLikeServiceImpl implements BoardLikeService {

    private final BoardLikeRepository boardLikeRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public boolean toggleLike(Long boardId, Long userId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Optional<BoardLike> existingLike = boardLikeRepository.findByBoardAndUser(board, user);

        if (existingLike.isPresent()) {
            boardLikeRepository.delete(existingLike.get());
            boardRepository.updateLikeCount(boardId, -1);
            return false;
        } else {
            boardLikeRepository.save(BoardLike.builder()
                    .board(board)
                    .user(user)
                    .build());
            boardRepository.updateLikeCount(boardId, 1);
            return true;
        }
    }
}
