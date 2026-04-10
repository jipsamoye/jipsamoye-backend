package com.jipsamoye.backend.domain.petPost.repository;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Page<PetPost> findByTitleContaining(String keyword, Pageable pageable);

    @Modifying
    @Query("UPDATE PetPost p SET p.likeCount = p.likeCount + :value WHERE p.id = :id")
    void updateLikeCount(@Param("id") Long id, @Param("value") int value);
}
