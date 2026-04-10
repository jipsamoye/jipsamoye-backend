package com.jipsamoye.backend.domain.petPost.service;

import com.jipsamoye.backend.domain.comment.repository.CommentRepository;
import com.jipsamoye.backend.domain.like.repository.LikeRepository;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostCreateRequest;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostUpdateRequest;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostResponse;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import com.jipsamoye.backend.global.scheduler.PopularPostScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetPostServiceImpl implements PetPostService {

    private final PetPostRepository petPostRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final PopularPostScheduler popularPostScheduler;

    @Override
    @Transactional
    public PetPostResponse createPost(PetPostCreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PetPost petPost = PetPost.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .imageUrls(request.getImageUrls())
                .build();

        PetPost saved = petPostRepository.save(petPost);
        return PetPostResponse.from(saved);
    }

    @Override
    public PetPostResponse getPost(Long id) {
        PetPost petPost = petPostRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return PetPostResponse.from(petPost);
    }

    @Override
    public PageResponse<PetPostListResponse> getPosts(int page, int size) {
        Page<PetPostListResponse> postPage = petPostRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(PetPostListResponse::from);
        return PageResponse.from(postPage);
    }

    @Override
    @Transactional
    public PetPostResponse updatePost(Long id, PetPostUpdateRequest request, Long userId) {
        PetPost petPost = petPostRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!petPost.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        petPost.update(request.getTitle(), request.getContent(), request.getImageUrls());
        return PetPostResponse.from(petPost);
    }

    @Override
    @Transactional
    public void deletePost(Long id, Long userId) {
        PetPost petPost = petPostRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!petPost.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // Like hard delete → Comment soft delete → PetPost soft delete
        likeRepository.deleteAllByPetPost(petPost);
        commentRepository.softDeleteAllByPetPost(petPost);
        petPost.softDelete();
    }

    @Override
    public PageResponse<PetPostListResponse> searchPosts(String keyword, int page, int size) {
        Page<PetPostListResponse> postPage = petPostRepository
                .findByTitleContaining(keyword, PageRequest.of(page, size))
                .map(PetPostListResponse::from);
        return PageResponse.from(postPage);
    }

    @Override
    public java.util.List<PetPostListResponse> getPopularPosts() {
        return popularPostScheduler.getPopularPosts();
    }
}
