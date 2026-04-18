package com.jipsamoye.backend.domain.boardLike.service;

public interface BoardLikeService {

    boolean toggleLike(Long boardId, Long userId);
}
