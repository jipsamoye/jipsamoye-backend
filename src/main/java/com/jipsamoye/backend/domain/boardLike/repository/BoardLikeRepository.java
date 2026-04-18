package com.jipsamoye.backend.domain.boardLike.repository;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.boardLike.entity.BoardLike;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoardLikeRepository extends JpaRepository<BoardLike, Long> {

    Optional<BoardLike> findByBoardAndUser(Board board, User user);

    boolean existsByBoardAndUser(Board board, User user);

    void deleteAllByBoard(Board board);

    void deleteAllByUser(User user);
}
