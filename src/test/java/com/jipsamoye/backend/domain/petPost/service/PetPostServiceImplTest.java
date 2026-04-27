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
import com.jipsamoye.backend.global.scheduler.PopularPostScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
