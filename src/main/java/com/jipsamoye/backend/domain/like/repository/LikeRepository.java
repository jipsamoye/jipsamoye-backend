package com.jipsamoye.backend.domain.like.repository;

import com.jipsamoye.backend.domain.like.entity.Like;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByPetPostAndUser(PetPost petPost, User user);

    boolean existsByPetPostAndUser(PetPost petPost, User user);

    void deleteAllByPetPost(PetPost petPost);
}
