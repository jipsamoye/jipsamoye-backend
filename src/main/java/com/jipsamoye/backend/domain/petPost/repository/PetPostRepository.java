package com.jipsamoye.backend.domain.petPost.repository;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PetPostRepository extends JpaRepository<PetPost, Long> {

    Page<PetPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PetPost> findAllByUser(User user, Pageable pageable);

    List<PetPost> findAllByUser(User user);

    long countByUser(User user);

    @Query("SELECT p FROM PetPost p WHERE p.createdAt >= :since ORDER BY p.likeCount DESC")
    List<PetPost> findPopularPosts(@Param("since") java.time.LocalDateTime since, Pageable pageable);

    @Query("SELECT p FROM PetPost p " +
            "WHERE p.createdAt >= :start AND p.createdAt < :end " +
            "ORDER BY p.likeCount DESC, p.id DESC")
    Page<PetPost> findRanking(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end,
                              Pageable pageable);

    @Modifying
    @Query("UPDATE PetPost p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.user = :user AND p.deletedAt IS NULL")
    void softDeleteAllByUser(@Param("user") User user);

    Page<PetPost> findByTitleContaining(String keyword, Pageable pageable);

    @Query("SELECT p FROM PetPost p ORDER BY p.likeCount DESC")
    List<PetPost> findTop10ByLikeCount(Pageable pageable);

    @Modifying
    @Query("UPDATE PetPost p SET p.likeCount = p.likeCount + :value WHERE p.id = :id")
    void updateLikeCount(@Param("id") Long id, @Param("value") int value);

    @Query("SELECT COALESCE(SUM(p.likeCount), 0) FROM PetPost p WHERE p.user.id = :userId")
    long sumLikeCountByUserId(@Param("userId") Long userId);
}
