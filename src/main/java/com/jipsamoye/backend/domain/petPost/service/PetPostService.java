package com.jipsamoye.backend.domain.petPost.service;

import com.jipsamoye.backend.domain.petPost.dto.request.PetPostCreateRequest;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostUpdateRequest;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostResponse;
import com.jipsamoye.backend.global.response.PageResponse;

public interface PetPostService {

    PetPostResponse createPost(PetPostCreateRequest request, Long userId);

    PetPostResponse getPost(Long id);

    PageResponse<?> getPosts(int page, int size);

    PetPostResponse updatePost(Long id, PetPostUpdateRequest request, Long userId);

    void deletePost(Long id, Long userId);
}
