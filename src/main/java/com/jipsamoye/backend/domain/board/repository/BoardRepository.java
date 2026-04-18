package com.jipsamoye.backend.domain.board.repository;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.entity.BoardCategory;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardRepository extends JpaRepository<Board, Long> {

    Page<Board> findAllByCategoryOrderByCreatedAtDesc(BoardCategory category, Pageable pageable);

    Page<Board> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Board> findByTitleContainingOrContentContaining(String title, String content, Pageable pageable);

    Page<Board> findByTitleContaining(String title, Pageable pageable);

    @Modifying
    @Query("UPDATE Board b SET b.likeCount = b.likeCount + :value WHERE b.id = :id")
    void updateLikeCount(@Param("id") Long id, @Param("value") int value);

    @Modifying
    @Query("UPDATE Board b SET b.commentCount = b.commentCount + :value WHERE b.id = :id")
    void updateCommentCount(@Param("id") Long id, @Param("value") int value);

    @Modifying
    @Query("UPDATE Board b SET b.deletedAt = CURRENT_TIMESTAMP WHERE b.user = :user AND b.deletedAt IS NULL")
    void softDeleteAllByUser(@Param("user") User user);

    long countByUser(User user);
}
