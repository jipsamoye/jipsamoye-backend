package com.jipsamoye.backend.domain.user.service;

import com.jipsamoye.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
}
