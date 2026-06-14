package com.jipsamoye.backend.domain.petPost.service;

import com.jipsamoye.backend.domain.image.service.ImageService;
import com.jipsamoye.backend.domain.like.repository.LikeRepository;
import com.jipsamoye.backend.domain.comment.repository.CommentRepository;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.response.PageResponse;
import com.jipsamoye.backend.global.response.SliceResponse;
import com.jipsamoye.backend.global.scheduler.PopularPostScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetPostServiceImplTest {

    @InjectMocks
    private PetPostServiceImpl petPostService;

    @Mock private PetPostRepository petPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private LikeRepository likeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ImageService imageService;
    @Mock private PopularPostScheduler popularPostScheduler;

    private static User activeUser() {
        return User.builder()
                .nickname("테스터")
                .profileImageUrl("https://cdn.example.com/me.jpg")
                .email("tester@example.com")
                .provider(Provider.KAKAO)
                .providerId("provider-123")
                .role(Role.USER)
                .build();
    }

    private static PetPost postBy(User user) {
        return PetPost.builder()
                .user(user)
                .title("팔로잉 게시글")
                .content("내용")
                .imageUrls(List.of("https://cdn.example.com/img1.jpg"))
                .build();
    }

    @Test
    @DisplayName("팔로잉이 없으면 빈 피드를 반환한다")
    void getFeed_noFollowings_returnsEmptyPage() {
        Page<PetPost> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(petPostRepository.findFeedByFollowerId(eq(1L), any(Pageable.class))).thenReturn(empty);

        PageResponse<PetPostListResponse> result = petPostService.getFeed(1L, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("팔로잉 게시글이 있으면 PetPostListResponse로 매핑해서 반환한다")
    void getFeed_withFollowings_returnsMappedPage() {
        User user = activeUser();
        PetPost post = postBy(user);
        Page<PetPost> page = new PageImpl<>(List.of(post), PageRequest.of(0, 20), 1);
        when(petPostRepository.findFeedByFollowerId(eq(42L), any(Pageable.class))).thenReturn(page);

        PageResponse<PetPostListResponse> result = petPostService.getFeed(42L, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("팔로잉 게시글");
        assertThat(result.getContent().get(0).nickname()).isEqualTo("테스터");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("userId가 repository에 그대로 전달된다")
    void getFeed_passesUserIdToRepository() {
        Page<PetPost> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(petPostRepository.findFeedByFollowerId(eq(99L), any(Pageable.class))).thenReturn(empty);

        petPostService.getFeed(99L, 0, 20);

        verify(petPostRepository).findFeedByFollowerId(eq(99L), any(Pageable.class));
    }

    // ===== 검색어 정제(sanitizeKeyword) 단위 테스트 =====

    @Test
    @DisplayName("일반 검색어는 그대로 유지된다")
    void sanitize_plainKeyword() {
        assertThat(PetPostServiceImpl.sanitizeKeyword("고양이")).isEqualTo("고양이");
    }

    @Test
    @DisplayName("BOOLEAN MODE 연산자 문자를 모두 제거한다")
    void sanitize_removesBooleanOperators() {
        assertThat(PetPostServiceImpl.sanitizeKeyword("고+양이")).isEqualTo("고양이");
        assertThat(PetPostServiceImpl.sanitizeKeyword("+-*\"()~<>@강아지")).isEqualTo("강아지");
    }

    @Test
    @DisplayName("앞뒤 공백과 제어문자를 제거한다")
    void sanitize_trimsAndRemovesControlChars() {
        assertThat(PetPostServiceImpl.sanitizeKeyword("  멍멍  ")).isEqualTo("멍멍");
        assertThat(PetPostServiceImpl.sanitizeKeyword("야\t옹\n이")).isEqualTo("야옹이");
    }

    @Test
    @DisplayName("null·빈문자열·특수문자만 있는 입력은 빈 문자열로 정제된다")
    void sanitize_emptyResults() {
        assertThat(PetPostServiceImpl.sanitizeKeyword(null)).isEmpty();
        assertThat(PetPostServiceImpl.sanitizeKeyword("")).isEmpty();
        assertThat(PetPostServiceImpl.sanitizeKeyword("   ")).isEmpty();
        assertThat(PetPostServiceImpl.sanitizeKeyword("+-*\"()~<>@")).isEmpty();
    }

    // ===== searchPosts 동작 테스트 (Slice 변환) =====

    @Test
    @DisplayName("정제 결과가 비면 쿼리를 실행하지 않고 빈 Slice를 반환한다")
    void searchPosts_emptySanitized_returnsEmptyWithoutQuery() {
        SliceResponse<PetPostListResponse> result = petPostService.searchPosts("+-*\"()", 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.getSize()).isEqualTo(20);
        verify(petPostRepository, never()).searchByTitleFulltext(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("정제어를 큰따옴표로 감싼 구문검색어로 repository에 전달한다")
    void searchPosts_passesPhraseQuery() {
        Slice<PetPost> empty = new SliceImpl<>(List.of(), PageRequest.of(0, 20), false);
        when(petPostRepository.searchByTitleFulltext(any(), any(Pageable.class))).thenReturn(empty);

        petPostService.searchPosts("고+양이", 0, 20);

        ArgumentCaptor<String> kwCaptor = ArgumentCaptor.forClass(String.class);
        verify(petPostRepository).searchByTitleFulltext(kwCaptor.capture(), any(Pageable.class));
        assertThat(kwCaptor.getValue()).isEqualTo("\"고양이\"");
    }

    @Test
    @DisplayName("검색 결과를 PetPostListResponse로 매핑하고 hasNext를 보존한다")
    void searchPosts_mapsAndPreservesHasNext() {
        User user = activeUser();
        PetPost post = postBy(user);
        Slice<PetPost> slice = new SliceImpl<>(List.of(post), PageRequest.of(0, 20), true);
        when(petPostRepository.searchByTitleFulltext(eq("\"팔로잉\""), any(Pageable.class))).thenReturn(slice);

        SliceResponse<PetPostListResponse> result = petPostService.searchPosts("팔로잉", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("팔로잉 게시글");
        assertThat(result.isHasNext()).isTrue();
        assertThat(result.getCurrentPage()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
    }
}
