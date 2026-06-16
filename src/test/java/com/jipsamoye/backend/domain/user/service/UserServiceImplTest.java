package com.jipsamoye.backend.domain.user.service;

import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.domain.user.dto.response.UserSearchItem;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    @Nested
    @DisplayName("searchUsers 메서드")
    class SearchUsersTest {

        private User searchUser(String nickname, String profileImageUrl) {
            User user = mock(User.class);
            lenient().when(user.getNickname()).thenReturn(nickname);
            lenient().when(user.getProfileImageUrl()).thenReturn(profileImageUrl);
            return user;
        }

        @Test
        @DisplayName("q가 null이면 빈 결과 반환, repository 미호출")
        void searchUsers_nullQuery_returnsEmpty() {
            PageResponse<UserSearchItem> response = userService.searchUsers(null, 1L, 0, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
            verify(userRepository, never()).searchByNickname(any(), any(), anyLong(), any());
            verify(followRepository, never()).findFollowingNicknamesIn(anyLong(), any());
        }

        @Test
        @DisplayName("q가 공백이면 빈 결과 반환, repository 미호출")
        void searchUsers_blankQuery_returnsEmpty() {
            PageResponse<UserSearchItem> response = userService.searchUsers("   ", 1L, 0, 20);

            assertThat(response.getContent()).isEmpty();
            verify(userRepository, never()).searchByNickname(any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("로그인 상태 - isFollowing batch 정확성: 팔로우한 닉네임만 true, findFollowingNicknamesIn 1회 호출")
        void searchUsers_loggedIn_isFollowingBatch() {
            User a = searchUser("cat", "imgA");
            User b = searchUser("cathy", null);
            User c = searchUser("scatter", "imgC");
            Page<User> page = new PageImpl<>(List.of(a, b, c), PageRequest.of(0, 20), 3);
            when(userRepository.searchByNickname(eq("cat"), eq("cat"), eq(1L), any(Pageable.class))).thenReturn(page);
            when(followRepository.findFollowingNicknamesIn(eq(1L), any()))
                    .thenReturn(Set.of("cat", "scatter"));

            PageResponse<UserSearchItem> response = userService.searchUsers("cat", 1L, 0, 20);

            assertThat(response.getContent()).extracting(UserSearchItem::nickname)
                    .containsExactly("cat", "cathy", "scatter");
            assertThat(response.getContent()).extracting(UserSearchItem::isFollowing)
                    .containsExactly(true, false, true);
            assertThat(response.getContent().get(0).profileImageUrl()).isEqualTo("imgA");
            verify(followRepository, times(1)).findFollowingNicknamesIn(eq(1L), any());
        }

        @Test
        @DisplayName("비로그인(currentUserId=null) - isFollowing 전부 false, followRepository 미호출, sentinel meId 전달")
        void searchUsers_notLoggedIn_allFalse() {
            User a = searchUser("cat", null);
            Page<User> page = new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1);
            when(userRepository.searchByNickname(eq("cat"), eq("cat"), eq(-1L), any(Pageable.class))).thenReturn(page);

            PageResponse<UserSearchItem> response = userService.searchUsers("cat", null, 0, 20);

            assertThat(response.getContent()).extracting(UserSearchItem::isFollowing)
                    .containsExactly(false);
            verify(followRepository, never()).findFollowingNicknamesIn(anyLong(), any());
        }

        @Test
        @DisplayName("결과가 비어 있으면 followRepository 미호출, 빈 PageResponse 매핑")
        void searchUsers_emptyResult_skipsFollowQuery() {
            Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(userRepository.searchByNickname(eq("없는유저"), eq("없는유저"), eq(1L), any(Pageable.class))).thenReturn(page);

            PageResponse<UserSearchItem> response = userService.searchUsers("없는유저", 1L, 0, 20);

            assertThat(response.getContent()).isEmpty();
            verify(followRepository, never()).findFollowingNicknamesIn(anyLong(), any());
        }

        @Test
        @DisplayName("페이징 메타데이터(totalElements/hasNext)가 Page에서 PageResponse로 보존")
        void searchUsers_pagingMetadataPreserved() {
            User a = searchUser("cat", null);
            Page<User> page = new PageImpl<>(List.of(a), PageRequest.of(0, 1), 5);
            when(userRepository.searchByNickname(eq("cat"), eq("cat"), eq(1L), any(Pageable.class))).thenReturn(page);
            when(followRepository.findFollowingNicknamesIn(eq(1L), any())).thenReturn(Set.of());

            PageResponse<UserSearchItem> response = userService.searchUsers("cat", 1L, 0, 1);

            assertThat(response.getTotalElements()).isEqualTo(5);
            assertThat(response.isHasNext()).isTrue();
            assertThat(response.getCurrentPage()).isZero();
        }

        @Test
        @DisplayName("q 앞뒤 공백은 trim되어 repository에 전달")
        void searchUsers_trimsQuery() {
            Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(userRepository.searchByNickname(eq("cat"), eq("cat"), eq(1L), any(Pageable.class))).thenReturn(page);

            userService.searchUsers("  cat  ", 1L, 0, 20);

            verify(userRepository).searchByNickname(eq("cat"), eq("cat"), eq(1L), any(Pageable.class));
        }

        @Test
        @DisplayName("LIKE 와일드카드 이스케이프: qLike에는 이스케이프된 값, qExact에는 원본 q 전달")
        void searchUsers_escapesLikeWildcards() {
            Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            // 입력 "a%_\\b" → qLike: 백슬래시 먼저 이스케이프 후 %,_ 이스케이프 → "a\%\_\\b"
            when(userRepository.searchByNickname(eq("a\\%\\_\\\\b"), eq("a%_\\b"), eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            userService.searchUsers("a%_\\b", 1L, 0, 20);

            verify(userRepository).searchByNickname(eq("a\\%\\_\\\\b"), eq("a%_\\b"), eq(1L), any(Pageable.class));
        }
    }
}
