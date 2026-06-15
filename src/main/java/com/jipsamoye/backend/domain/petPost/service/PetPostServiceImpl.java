package com.jipsamoye.backend.domain.petPost.service;

import com.jipsamoye.backend.domain.comment.repository.CommentRepository;
import com.jipsamoye.backend.domain.image.service.ImageService;
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
import com.jipsamoye.backend.global.response.CursorResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import com.jipsamoye.backend.global.response.SliceResponse;
import com.jipsamoye.backend.global.scheduler.PopularPostScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetPostServiceImpl implements PetPostService {

    private final PetPostRepository petPostRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final ImageService imageService;
    private final PopularPostScheduler popularPostScheduler;

    @Override
    @Transactional
    public PetPostResponse createPost(PetPostCreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PetPost petPost = PetPost.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .imageUrls(request.imageUrls())
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
    public CursorResponse<PetPostListResponse> getPosts(Long cursor, int size) {
        // hasNext 판정을 위해 size+1개를 조회한 뒤 초과분을 잘라낸다 (chat 모듈과 동일한 패턴, reverse는 불필요)
        PageRequest pageRequest = PageRequest.of(0, size + 1);
        List<PetPost> fetched = (cursor == null)
                ? petPostRepository.findByOrderByIdDesc(pageRequest)
                : petPostRepository.findByIdLessThanOrderByIdDesc(cursor, pageRequest);

        boolean hasNext = fetched.size() > size;
        if (hasNext) {
            fetched = fetched.subList(0, size);
        }

        List<PetPostListResponse> content = fetched.stream()
                .map(PetPostListResponse::from)
                .toList();

        Long nextCursor = (hasNext && !content.isEmpty())
                ? content.get(content.size() - 1).id()
                : null;

        return CursorResponse.of(content, size, hasNext, nextCursor);
    }

    @Override
    @Transactional
    public PetPostResponse updatePost(Long id, PetPostUpdateRequest request, Long userId) {
        PetPost petPost = petPostRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!petPost.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 수정 시 빠진 이미지 S3 삭제
        if (request.imageUrls() != null) {
            java.util.List<String> removedUrls = petPost.getImageUrls().stream()
                    .filter(url -> !request.imageUrls().contains(url))
                    .toList();
            imageService.deleteImages(removedUrls);
        }

        petPost.update(request.title(), request.content(), request.imageUrls());
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

        // Like hard delete → Comment soft delete → S3 이미지 삭제 → PetPost soft delete
        likeRepository.deleteAllByPetPost(petPost);
        commentRepository.softDeleteAllByPetPost(petPost);
        imageService.deleteImages(petPost.getImageUrls());
        petPost.softDelete();
    }

    /**
     * BOOLEAN MODE 연산자/제어문자 제거 + trim. 정제 결과가 비면 빈 문자열.
     * 제거 대상: + - * " ( ) ~ < > @ 및 모든 제어문자(\p{Cntrl}).
     */
    private static final Pattern BOOLEAN_OPERATORS = Pattern.compile("[+\\-*\"()~<>@]|\\p{Cntrl}");

    static String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return BOOLEAN_OPERATORS.matcher(keyword).replaceAll("").trim();
    }

    @Override
    public SliceResponse<PetPostListResponse> searchPosts(String keyword, int page, int size) {
        String sanitized = sanitizeKeyword(keyword);
        PageRequest pageRequest = PageRequest.of(page, size);

        // 정제 후 빈 문자열이면 쿼리를 실행하지 않고 빈 결과 반환
        if (sanitized.isEmpty()) {
            Slice<PetPostListResponse> empty = new SliceImpl<>(java.util.List.of(), pageRequest, false);
            return SliceResponse.from(empty);
        }

        // 구문검색을 위해 큰따옴표로 감싸 BOOLEAN MODE 구(phrase)로 전달 → LIKE와 동일한 연속 부분문자열 매칭
        String phraseQuery = "\"" + sanitized + "\"";
        Slice<PetPostListResponse> postSlice = petPostRepository
                .searchByTitleFulltext(phraseQuery, pageRequest)
                .map(PetPostListResponse::from);
        return SliceResponse.from(postSlice);
    }

    @Override
    public java.util.List<PetPostListResponse> getPopularPosts() {
        return popularPostScheduler.getPopularPosts();
    }

    @Override
    public java.util.List<PetPostListResponse> getTop10Posts() {
        return petPostRepository.findTop10ByLikeCount(PageRequest.of(0, 10))
                .stream()
                .map(PetPostListResponse::from)
                .toList();
    }

    @Override
    public PageResponse<PetPostListResponse> getFeed(Long userId, int page, int size) {
        Page<PetPostListResponse> feedPage = petPostRepository
                .findFeedByFollowerId(userId, PageRequest.of(page, size))
                .map(PetPostListResponse::from);
        return PageResponse.from(feedPage);
    }
}
