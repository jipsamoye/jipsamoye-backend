package com.jipsamoye.backend.domain.comment.repository;

import com.jipsamoye.backend.domain.comment.entity.Comment;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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
}
