package com.jipsamoye.backend.domain.comment.repository;

import com.jipsamoye.backend.domain.comment.entity.Comment;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
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

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findAllByPetPostOrderByCreatedAtDesc(PetPost petPost, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.petPost = :petPost")
    void deleteAllByPetPost(PetPost petPost);

    @Modifying
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.petPost = :petPost AND c.deletedAt IS NULL")
    void softDeleteAllByPetPost(PetPost petPost);

    @Modifying
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.user = :user AND c.deletedAt IS NULL")
    void softDeleteAllByUser(User user);

    @Query("SELECT c FROM Comment c WHERE c.petPost.id = :postId AND c.parent IS NULL ORDER BY c.createdAt DESC")
    Page<Comment> findParentsByPetPostId(@Param("postId") Long postId, Pageable pageable);

    @Query("SELECT c.parent.id, COUNT(c) FROM Comment c WHERE c.parent.id IN :parentIds GROUP BY c.parent.id")
    List<Object[]> countRepliesGroupedByParentIds(@Param("parentIds") List<Long> parentIds);

    @Query(value = """
            SELECT * FROM (
              SELECT c.*, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY created_at ASC) AS rn
              FROM comments c
              WHERE c.parent_id IN (:parentIds) AND c.deleted_at IS NULL
            ) t WHERE t.rn <= 3
            """, nativeQuery = true)
    List<Comment> findTop3RepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    @Query("SELECT c FROM Comment c WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC")
    Page<Comment> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);

    long countByParentAndDeletedAtIsNull(Comment parent);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comment c WHERE c.id = :id")
    Optional<Comment> findByIdForUpdate(@Param("id") Long id);
}
