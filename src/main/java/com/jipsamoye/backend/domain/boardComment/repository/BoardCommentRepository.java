package com.jipsamoye.backend.domain.boardComment.repository;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.boardComment.entity.BoardComment;
import com.jipsamoye.backend.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

    Page<BoardComment> findAllByBoardOrderByCreatedAtDesc(Board board, Pageable pageable);

    @Modifying
    @Query("UPDATE BoardComment c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.board = :board AND c.deletedAt IS NULL")
    void softDeleteAllByBoard(@Param("board") Board board);

    @Modifying
    @Query("UPDATE BoardComment c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.user = :user AND c.deletedAt IS NULL")
    void softDeleteAllByUser(@Param("user") User user);

    @Query("SELECT c FROM BoardComment c WHERE c.board.id = :boardId AND c.parent IS NULL ORDER BY c.createdAt DESC")
    Page<BoardComment> findParentsByBoardId(@Param("boardId") Long boardId, Pageable pageable);

    @Query("SELECT c.parent.id, COUNT(c) FROM BoardComment c WHERE c.parent.id IN :parentIds GROUP BY c.parent.id")
    List<Object[]> countRepliesGroupedByParentIds(@Param("parentIds") List<Long> parentIds);

    @Query(value = """
            SELECT * FROM (
              SELECT c.*, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY created_at ASC) AS rn
              FROM board_comments c
              WHERE c.parent_id IN (:parentIds) AND c.deleted_at IS NULL
            ) t WHERE t.rn <= 3
            """, nativeQuery = true)
    List<BoardComment> findTop3RepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    @Query("SELECT c FROM BoardComment c WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC")
    Page<BoardComment> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);

    long countByParentAndDeletedAtIsNull(BoardComment parent);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BoardComment c WHERE c.id = :id")
    Optional<BoardComment> findByIdForUpdate(@Param("id") Long id);
}
