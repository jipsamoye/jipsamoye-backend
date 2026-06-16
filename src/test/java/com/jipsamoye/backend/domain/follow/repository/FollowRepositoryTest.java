package com.jipsamoye.backend.domain.follow.repository;

import com.jipsamoye.backend.domain.follow.entity.Follow;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("datajpatest")
@DisplayName("FollowRepository.findFollowingNicknamesIn - 팔로우 닉네임 batch 조회")
class FollowRepositoryTest {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    private User me;
    private User catUser;
    private User dogUser;
    private User foxUser;

    @BeforeEach
    void setUp() {
        me = userRepository.save(newUser("나", "me@test.com", "pid-me"));
        catUser = userRepository.save(newUser("cat", "cat@test.com", "pid-cat"));
        dogUser = userRepository.save(newUser("dog", "dog@test.com", "pid-dog"));
        foxUser = userRepository.save(newUser("fox", "fox@test.com", "pid-fox"));

        // 나는 cat, fox를 팔로우 (dog는 미팔로우)
        followRepository.save(Follow.builder().follower(me).following(catUser).build());
        followRepository.save(Follow.builder().follower(me).following(foxUser).build());
    }

    private User newUser(String nickname, String email, String providerId) {
        return User.builder()
                .nickname(nickname)
                .email(email)
                .provider(Provider.GOOGLE)
                .providerId(providerId)
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("닉네임 집합 중 내가 팔로우한 닉네임만 반환한다")
    void findFollowingNicknamesIn_returnsOnlyFollowed() {
        Set<String> result = followRepository.findFollowingNicknamesIn(
                me.getId(), List.of("cat", "dog", "fox"));

        assertThat(result).containsExactlyInAnyOrder("cat", "fox");
        assertThat(result).doesNotContain("dog");
    }

    @Test
    @DisplayName("입력 닉네임에 포함되지 않은 팔로우는 결과에 없다")
    void findFollowingNicknamesIn_filtersByInputSet() {
        Set<String> result = followRepository.findFollowingNicknamesIn(
                me.getId(), List.of("dog"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("팔로우가 전혀 없는 유저는 빈 set을 반환한다")
    void findFollowingNicknamesIn_noFollows_returnsEmpty() {
        Set<String> result = followRepository.findFollowingNicknamesIn(
                catUser.getId(), List.of("cat", "dog", "fox"));

        assertThat(result).isEmpty();
    }
}
