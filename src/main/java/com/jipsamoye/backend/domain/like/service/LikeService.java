package com.jipsamoye.backend.domain.like.service;

public interface LikeService {

    boolean toggleLike(Long postId, Long userId);
}
