package com.jipsamoye.backend.domain.boardComment.repository;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.boardComment.entity.BoardComment;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

    Page<BoardComment> findAllByBoardOrderByCreatedAtDesc(Board board, Pageable pageable);

    @Modifying
    @Query("UPDATE BoardComment c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.board = :board AND c.deletedAt IS NULL")
    void softDeleteAllByBoard(@Param("board") Board board);

    @Modifying
    @Query("UPDATE BoardComment c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.user = :user AND c.deletedAt IS NULL")
    void softDeleteAllByUser(@Param("user") User user);
}
