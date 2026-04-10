package com.jipsamoye.backend.domain.like.service;

public interface LikeService {

    /**
     * 좋아요 토글 — 있으면 취소(hard delete), 없으면 추가
     * likeCount는 @Query로 원자적 증감
     * @return true: 좋아요 추가, false: 좋아요 취소
     */
    boolean toggleLike(Long postId, Long userId);
}
