package com.jipsamoye.backend.domain.user.service;

import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetPostRepository petPostRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("닉네임 중복 확인 - 사용 가능")
    void isNicknameAvailable_true() {
        when(userRepository.existsByNickname("새닉네임")).thenReturn(false);

        assertThat(userService.isNicknameAvailable("새닉네임")).isTrue();
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 이미 사용 중")
    void isNicknameAvailable_false() {
        when(userRepository.existsByNickname("멍집사")).thenReturn(true);

        assertThat(userService.isNicknameAvailable("멍집사")).isFalse();
    }

    private User mockActiveUser(long id, String nickname) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.isDeleted()).thenReturn(false);
        lenient().when(user.getNickname()).thenReturn(nickname);
        lenient().when(user.getProfileImageUrl()).thenReturn(null);
        lenient().when(user.getBio()).thenReturn(null);
        lenient().when(user.getCoverImageUrl()).thenReturn(null);
        return user;
    }

    private void stubRankingCalls(User user, long userId, long postCount, long followerCount,
                                  long followingCount, long totalLikes, long moreCount) {
        when(petPostRepository.countByUser(user)).thenReturn(postCount);
        when(followRepository.countByFollowing(user)).thenReturn(followerCount);
        when(followRepository.countByFollower(user)).thenReturn(followingCount);
        when(petPostRepository.sumLikeCountByUserId(userId)).thenReturn(totalLikes);
        if (totalLikes > 0) {
            when(userRepository.countActiveUsersWithMoreLikesThan(totalLikes)).thenReturn(moreCount);
        }
    }

    @Test
    @DisplayName("getProfile - 좋아요 합계가 가장 높으면 ranking=1, totalLikeCount는 합계")
    void getProfile_topRanker_rankingIsOne() {
        User user = mockActiveUser(1L, "탑유저");
        when(userRepository.findByNickname("탑유저")).thenReturn(Optional.of(user));
        stubRankingCalls(user, 1L, 5L, 10L, 20L, 100L, 0L);

        UserResponse response = userService.getProfile("탑유저", null);

        assertThat(response.ranking()).isEqualTo(1L);
        assertThat(response.totalLikeCount()).isEqualTo(100L);
        assertThat(response.postCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getProfile - 동률 처리: 나보다 큰 유저가 2명이면 ranking=3 (같은점수=같은등수, 다음 등수 건너뜀)")
    void getProfile_tieRule_rankingSkipsAfterTies() {
        User user = mockActiveUser(2L, "동률유저");
        when(userRepository.findByNickname("동률유저")).thenReturn(Optional.of(user));
        stubRankingCalls(user, 2L, 3L, 0L, 0L, 50L, 2L);

        UserResponse response = userService.getProfile("동률유저", null);

        assertThat(response.ranking()).isEqualTo(3L);
        assertThat(response.totalLikeCount()).isEqualTo(50L);
    }

    @Test
    @DisplayName("getProfile - 좋아요 0인 유저는 ranking=null, totalLikeCount=0")
    void getProfile_zeroLikes_rankingNull() {
        User user = mockActiveUser(3L, "신규유저");
        when(userRepository.findByNickname("신규유저")).thenReturn(Optional.of(user));
        stubRankingCalls(user, 3L, 0L, 0L, 0L, 0L, 0L);

        UserResponse response = userService.getProfile("신규유저", null);

        assertThat(response.ranking()).isNull();
        assertThat(response.totalLikeCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("getProfile - 탈퇴 유저는 USER_NOT_FOUND 예외")
    void getProfile_deletedUser_throws() {
        User user = mock(User.class);
        when(user.isDeleted()).thenReturn(true);
        when(userRepository.findByNickname("탈퇴유저")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getProfile("탈퇴유저", null))
                .isInstanceOf(BusinessException.class);
    }

    @Nested
    @DisplayName("getProfile isFollowing 필드")
    class GetProfileIsFollowingTest {

        @Test
        @DisplayName("비로그인(currentUserId=null)이면 isFollowing=false이고 existsByFollowerAndFollowing 미호출")
        void getProfile_notLoggedIn_isFollowingFalse() {
            User target = mockActiveUser(10L, "대상유저");
            when(userRepository.findByNickname("대상유저")).thenReturn(Optional.of(target));
            stubRankingCalls(target, 10L, 0L, 0L, 0L, 0L, 0L);

            UserResponse response = userService.getProfile("대상유저", null);

            assertThat(response.isFollowing()).isFalse();
            verify(followRepository, never()).existsByFollowerAndFollowing(any(), any());
        }

        @Test
        @DisplayName("본인 조회(currentUserId == 대상 id)이면 isFollowing=false이고 existsByFollowerAndFollowing 미호출")
        void getProfile_selfLookup_isFollowingFalse() {
            User target = mockActiveUser(10L, "대상유저");
            when(userRepository.findByNickname("대상유저")).thenReturn(Optional.of(target));
            stubRankingCalls(target, 10L, 0L, 0L, 0L, 0L, 0L);

            UserResponse response = userService.getProfile("대상유저", 10L);

            assertThat(response.isFollowing()).isFalse();
            verify(followRepository, never()).existsByFollowerAndFollowing(any(), any());
        }

        @Test
        @DisplayName("팔로우 중이면 isFollowing=true")
        void getProfile_following_isFollowingTrue() {
            User me = mockActiveUser(1L, "나");
            User target = mockActiveUser(10L, "대상유저");
            when(userRepository.findByNickname("대상유저")).thenReturn(Optional.of(target));
            when(userRepository.findById(1L)).thenReturn(Optional.of(me));
            when(followRepository.existsByFollowerAndFollowing(me, target)).thenReturn(true);
            stubRankingCalls(target, 10L, 0L, 0L, 0L, 0L, 0L);

            UserResponse response = userService.getProfile("대상유저", 1L);

            assertThat(response.isFollowing()).isTrue();
        }

        @Test
        @DisplayName("미팔로우이면 isFollowing=false")
        void getProfile_notFollowing_isFollowingFalse() {
            User me = mockActiveUser(1L, "나");
            User target = mockActiveUser(10L, "대상유저");
            when(userRepository.findByNickname("대상유저")).thenReturn(Optional.of(target));
            when(userRepository.findById(1L)).thenReturn(Optional.of(me));
            when(followRepository.existsByFollowerAndFollowing(me, target)).thenReturn(false);
            stubRankingCalls(target, 10L, 0L, 0L, 0L, 0L, 0L);

            UserResponse response = userService.getProfile("대상유저", 1L);

            assertThat(response.isFollowing()).isFalse();
        }
    }
}
