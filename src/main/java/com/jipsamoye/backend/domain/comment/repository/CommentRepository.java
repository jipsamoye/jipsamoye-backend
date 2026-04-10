package com.jipsamoye.backend.domain.comment.repository;

import com.jipsamoye.backend.domain.comment.entity.Comment;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findAllByPetPostOrderByCreatedAtDesc(PetPost petPost, Pageable pageable);

    void deleteAllByPetPost(PetPost petPost);
}
