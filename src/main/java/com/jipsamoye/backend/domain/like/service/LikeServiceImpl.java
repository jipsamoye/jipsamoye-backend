package com.jipsamoye.backend.domain.like.service;

import com.jipsamoye.backend.domain.like.entity.Like;
import com.jipsamoye.backend.domain.like.repository.LikeRepository;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeServiceImpl implements LikeService {

    private final LikeRepository likeRepository;
    private final PetPostRepository petPostRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public boolean toggleLike(Long postId, Long userId) {
        PetPost petPost = petPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Optional<Like> existingLike = likeRepository.findByPetPostAndUser(petPost, user);

        if (existingLike.isPresent()) {
            likeRepository.delete(existingLike.get());
            petPostRepository.updateLikeCount(postId, -1);
            return false;
        } else {
            likeRepository.save(Like.builder()
                    .petPost(petPost)
                    .user(user)
                    .build());
            petPostRepository.updateLikeCount(postId, 1);
            return true;
        }
    }
}
