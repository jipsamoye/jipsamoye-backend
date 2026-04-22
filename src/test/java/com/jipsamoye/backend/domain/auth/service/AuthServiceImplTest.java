package com.jipsamoye.backend.domain.auth.service;

import com.jipsamoye.backend.domain.comment.repository.CommentRepository;
import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.like.repository.LikeRepository;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private UserRepository userRepository;
    private PetPostRepository petPostRepository;
    private CommentRepository commentRepository;
    private LikeRepository likeRepository;
    private FollowRepository followRepository;
    private HttpSession httpSession;
    private HttpServletResponse httpServletResponse;
    private ServerProperties serverProperties;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        petPostRepository = mock(PetPostRepository.class);
        commentRepository = mock(CommentRepository.class);
        likeRepository = mock(LikeRepository.class);
        followRepository = mock(FollowRepository.class);
        httpSession = mock(HttpSession.class);
        httpServletResponse = mock(HttpServletResponse.class);

        serverProperties = new ServerProperties();
        serverProperties.getServlet().getSession().setTimeout(Duration.ofHours(2));
        serverProperties.getServlet().getSession().getCookie().setDomain("jipsamoye.com");
        serverProperties.getServlet().getSession().getCookie().setSecure(true);

        authService = new AuthServiceImpl(
                userRepository,
                petPostRepository,
                commentRepository,
                likeRepository,
                followRepository,
                httpSession,
                httpServletResponse,
                serverProperties
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createGuest - 둘러보기 세션 생성 후 has_session 힌트 쿠키를 session cookie로 발급한다")
    void createGuest_setsSessionHintCookie() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        authService.createGuest();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).startsWith("has_session=1");
        assertThat(header).contains("Domain=jipsamoye.com");
        // Max-Age 없음: JSESSIONID와 수명 일치, Spring Session rolling과 어긋나지 않음
        assertThat(header).doesNotContain("Max-Age");
        assertThat(header).doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("logout - 세션 무효화 후 힌트 쿠키를 Max-Age=0으로 삭제한다")
    void logout_clearsSessionHintCookie() {
        authService.logout();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).startsWith("has_session=");
        assertThat(header).contains("Max-Age=0");
        assertThat(header).contains("Domain=jipsamoye.com");
        verify(httpSession).invalidate();
    }

    @Test
    @DisplayName("withdraw - 회원 탈퇴 시 힌트 쿠키를 Max-Age=0으로 삭제한다")
    void withdraw_clearsSessionHintCookie() {
        User user = User.builder()
                .nickname("손님abc123")
                .email("guest_test@jipsamoye.com")
                .provider(Provider.GUEST)
                .providerId("test-id")
                .role(Role.GUEST)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.withdraw(1L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), captor.capture());
        String header = captor.getValue();

        assertThat(header).startsWith("has_session=");
        assertThat(header).contains("Max-Age=0");
        verify(httpSession).invalidate();
    }

    @Test
    @DisplayName("getMe - 단순 조회 API는 힌트 쿠키를 건드리지 않는다")
    void getMe_doesNotTouchHintCookie() {
        User user = User.builder()
                .nickname("손님xyz")
                .email("guest_xyz@jipsamoye.com")
                .provider(Provider.GUEST)
                .providerId("xyz")
                .role(Role.GUEST)
                .build();
        ReflectionTestUtils.setField(user, "id", 2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(petPostRepository.countByUser(user)).thenReturn(0L);
        when(followRepository.countByFollowing(user)).thenReturn(0L);
        when(followRepository.countByFollower(user)).thenReturn(0L);

        authService.getMe(2L);

        verify(httpServletResponse, never()).addHeader(eq(HttpHeaders.SET_COOKIE), any(String.class));
    }
}
