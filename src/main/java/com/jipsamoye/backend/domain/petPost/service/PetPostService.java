package com.jipsamoye.backend.domain.petPost.service;

import com.jipsamoye.backend.domain.petPost.dto.request.PetPostCreateRequest;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostUpdateRequest;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostResponse;
import com.jipsamoye.backend.global.response.CursorResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import com.jipsamoye.backend.global.response.SliceResponse;

import java.util.List;

public interface PetPostService {

    PetPostResponse createPost(PetPostCreateRequest request, Long userId);

    PetPostResponse getPost(Long id);

    /**
     * 최신 자랑 목록 — 커서(keyset) 페이징. cursor 생략 시 최신부터, nextCursor로 다음 페이지 요청.
     */
    CursorResponse<PetPostListResponse> getPosts(Long cursor, int size);

    PetPostResponse updatePost(Long id, PetPostUpdateRequest request, Long userId);

    /**
     * 게시글 삭제 — Like hard delete → Comment soft delete → PetPost soft delete
     * 본인 게시글만 삭제 가능, 아니면 FORBIDDEN(403)
     */
    void deletePost(Long id, Long userId);

    SliceResponse<PetPostListResponse> searchPosts(String keyword, int page, int size);

    /**
     * 오늘의 멍냥 — 스케줄러가 1시간마다 캐싱한 데이터 반환 (DB 직접 조회 X)
     */
    List<PetPostListResponse> getPopularPosts();

    List<PetPostListResponse> getTop10Posts();

    PageResponse<PetPostListResponse> getFeed(Long userId, int page, int size);
}
