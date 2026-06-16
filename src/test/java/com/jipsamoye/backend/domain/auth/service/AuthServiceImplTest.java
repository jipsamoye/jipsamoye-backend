package com.jipsamoye.backend.domain.auth.service;

import com.jipsamoye.backend.domain.auth.client.NaverApiClient;
import com.jipsamoye.backend.domain.auth.client.dto.response.NaverProfileResponse;
import com.jipsamoye.backend.domain.auth.client.dto.response.NaverTokenResponse;
import com.jipsamoye.backend.domain.auth.dto.request.NaverLoginRequest;
import com.jipsamoye.backend.domain.auth.dto.response.NaverLoginResponse;
import com.jipsamoye.backend.domain.comment.repository.CommentRepository;
import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.like.repository.LikeRepository;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private NaverApiClient naverApiClient;

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
        naverApiClient = mock(NaverApiClient.class);

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
                serverProperties,
                naverApiClient
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 기존 게스트 테스트 (issueSession 추출 후 동작 보존 확인)
    // ──────────────────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────────────────
    // 네이버 로그인 테스트
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("naverLogin")
    class NaverLoginTest {

        private static final String ACCESS_TOKEN = "test-access-token";
        private static final String PROVIDER_ID = "naver-user-123";

        private NaverTokenResponse tokenResponse() {
            return new NaverTokenResponse(ACCESS_TOKEN, "refresh", "bearer", "3600", null, null);
        }

        private NaverProfileResponse profileResponse(String nickname, String email, String profileImage) {
            return new NaverProfileResponse("00", "success",
                    new NaverProfileResponse.NaverProfile(PROVIDER_ID, nickname, email, profileImage));
        }

        @Test
        @DisplayName("신규 유저 가입 후 세션 발급 — provider=NAVER, role=USER, isNewUser=true, has_session 쿠키 발급")
        void newUser_registersAndIssuesSession() {
            when(naverApiClient.exchangeToken("code", "state")).thenReturn(tokenResponse());
            when(naverApiClient.getProfile(ACCESS_TOKEN))
                    .thenReturn(profileResponse("테스트유저", "test@naver.com", "https://img.url"));
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, PROVIDER_ID))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByNickname(any(String.class))).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 10L);
                return u;
            });

            NaverLoginResponse result = authService.naverLogin(new NaverLoginRequest("code", "state"));

            assertThat(result.isNewUser()).isTrue();
            assertThat(result.user()).isNotNull();

            // 신규 가입자는 카운트 조회 없이 0을 반환해야 한다
            assertThat(result.user().postCount()).isEqualTo(0L);
            assertThat(result.user().followerCount()).isEqualTo(0L);
            assertThat(result.user().followingCount()).isEqualTo(0L);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertThat(saved.getProvider()).isEqualTo(Provider.NAVER);
            assertThat(saved.getProviderId()).isEqualTo(PROVIDER_ID);
            assertThat(saved.getRole()).isEqualTo(Role.USER);
            assertThat(saved.getEmail()).isEqualTo("test@naver.com");
            assertThat(saved.getProfileImageUrl()).isEqualTo("https://img.url");

            // 네이버 닉네임에 의존하지 않고 항상 "집사" 랜덤 닉네임을 생성한다
            assertThat(saved.getNickname()).startsWith("집사");
            assertThat(saved.getNickname()).isNotEqualTo("테스트유저");
            // 생성 닉네임이 DB 제약(length=10)을 넘지 않는다
            assertThat(saved.getNickname()).hasSizeLessThanOrEqualTo(10);

            // SecurityContext 인증 설정 확인
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

            // has_session 쿠키 발급 확인
            ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
            verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), cookieCaptor.capture());
            assertThat(cookieCaptor.getValue()).startsWith("has_session=1");
        }

        @Test
        @DisplayName("기존 유저는 저장 없이 로그인 — isNewUser=false, 세션 발급됨")
        void existingUser_loginWithoutSave() {
            User existing = User.builder()
                    .nickname("기존유저")
                    .email("existing@naver.com")
                    .provider(Provider.NAVER)
                    .providerId(PROVIDER_ID)
                    .role(Role.USER)
                    .build();
            ReflectionTestUtils.setField(existing, "id", 20L);

            when(naverApiClient.exchangeToken("code", "state")).thenReturn(tokenResponse());
            when(naverApiClient.getProfile(ACCESS_TOKEN))
                    .thenReturn(profileResponse("기존유저", "existing@naver.com", null));
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, PROVIDER_ID))
                    .thenReturn(Optional.of(existing));
            when(petPostRepository.countByUser(existing)).thenReturn(3L);
            when(followRepository.countByFollowing(existing)).thenReturn(2L);
            when(followRepository.countByFollower(existing)).thenReturn(1L);

            NaverLoginResponse result = authService.naverLogin(new NaverLoginRequest("code", "state"));

            assertThat(result.isNewUser()).isFalse();
            verify(userRepository, never()).save(any());

            // 실제 카운트가 응답에 반영되는지 검증
            assertThat(result.user().postCount()).isEqualTo(3L);
            assertThat(result.user().followerCount()).isEqualTo(2L);
            assertThat(result.user().followingCount()).isEqualTo(1L);

            // 세션 발급 확인
            ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
            verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), cookieCaptor.capture());
            assertThat(cookieCaptor.getValue()).startsWith("has_session=1");
        }

        @Test
        @DisplayName("네이버 닉네임이 와도 무시하고 '집사' 랜덤 닉네임을 생성한다")
        void naverNickname_ignoredAndRandomGenerated() {
            when(naverApiClient.exchangeToken("code", "state")).thenReturn(tokenResponse());
            when(naverApiClient.getProfile(ACCESS_TOKEN))
                    .thenReturn(profileResponse("짧은닉", "user@naver.com", null));
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, PROVIDER_ID))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByNickname(any(String.class))).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 11L);
                return u;
            });

            authService.naverLogin(new NaverLoginRequest("code", "state"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getNickname()).startsWith("집사");
            assertThat(captor.getValue().getNickname()).isNotEqualTo("짧은닉");
        }

        @Test
        @DisplayName("생성된 닉네임이 중복이면 재시도 후 '집사' prefix 닉네임을 생성한다")
        void duplicateGeneratedNickname_retriesAndSucceeds() {
            when(naverApiClient.exchangeToken("code", "state")).thenReturn(tokenResponse());
            when(naverApiClient.getProfile(ACCESS_TOKEN))
                    .thenReturn(profileResponse("중복닉네임", "user@naver.com", null));
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, PROVIDER_ID))
                    .thenReturn(Optional.empty());
            // 첫 번째 생성 후보는 중복, 두 번째부터 사용 가능
            when(userRepository.existsByNickname(any(String.class)))
                    .thenReturn(true)
                    .thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 12L);
                return u;
            });

            authService.naverLogin(new NaverLoginRequest("code", "state"));

            // existsByNickname 호출 인자 2개를 캡처: [중복된 첫 후보, 사용 가능한 두 번째 후보]
            ArgumentCaptor<String> nicknameCaptor = ArgumentCaptor.forClass(String.class);
            verify(userRepository, times(2)).existsByNickname(nicknameCaptor.capture());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getNickname()).startsWith("집사");

            // 중복된 첫 후보를 버리고, 마지막(두 번째) 후보를 저장했는지 직접 검증
            assertThat(nicknameCaptor.getAllValues()).hasSize(2);
            String secondCandidate = nicknameCaptor.getAllValues().get(1);
            assertThat(captor.getValue().getNickname()).isEqualTo(secondCandidate);
        }

        @Test
        @DisplayName("긴 네이버 닉네임이 와도 무시하고 '집사' 랜덤 닉네임을 생성한다")
        void longNaverNickname_ignoredAndRandomGenerated() {
            String longNickname = "12345678901234"; // 14자
            when(naverApiClient.exchangeToken("code", "state")).thenReturn(tokenResponse());
            when(naverApiClient.getProfile(ACCESS_TOKEN))
                    .thenReturn(profileResponse(longNickname, "user@naver.com", null));
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, PROVIDER_ID))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByNickname(any(String.class))).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 13L);
                return u;
            });

            authService.naverLogin(new NaverLoginRequest("code", "state"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getNickname()).startsWith("집사");
            assertThat(captor.getValue().getNickname()).isNotEqualTo(longNickname);
        }

        @Test
        @DisplayName("닉네임 생성이 5회 모두 중복이면 BusinessException을 던진다")
        void nicknameGenerationFailsAfterRetries_throwsException() {
            when(naverApiClient.exchangeToken("code", "state")).thenReturn(tokenResponse());
            when(naverApiClient.getProfile(ACCESS_TOKEN))
                    .thenReturn(profileResponse("테스트유저", "user@naver.com", null));
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, PROVIDER_ID))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByNickname(any(String.class))).thenReturn(true);

            assertThatThrownBy(() -> authService.naverLogin(new NaverLoginRequest("code", "state")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("닉네임·이메일 null이면 폴백 — 자동 생성 닉네임 + naver_{id}@jipsamoye.com")
        void nullNicknameAndEmail_usesFallback() {
            when(naverApiClient.exchangeToken("code", "state")).thenReturn(tokenResponse());
            when(naverApiClient.getProfile(ACCESS_TOKEN))
                    .thenReturn(profileResponse(null, null, null));
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, PROVIDER_ID))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByNickname(any(String.class))).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 14L);
                return u;
            });

            authService.naverLogin(new NaverLoginRequest("code", "state"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getNickname()).startsWith("집사");
            assertThat(captor.getValue().getEmail()).isEqualTo("naver_" + PROVIDER_ID + "@jipsamoye.com");
        }

        @Test
        @DisplayName("토큰 교환 실패 시 예외 전파 — save 없음, 쿠키 발급 없음")
        void tokenExchangeFailure_propagatesException() {
            when(naverApiClient.exchangeToken("bad-code", "state"))
                    .thenThrow(new BusinessException(ErrorCode.NAVER_TOKEN_EXCHANGE_FAILED));

            assertThatThrownBy(() -> authService.naverLogin(new NaverLoginRequest("bad-code", "state")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NAVER_TOKEN_EXCHANGE_FAILED));

            verify(userRepository, never()).save(any());
            verify(httpServletResponse, never()).addHeader(eq(HttpHeaders.SET_COOKIE), any());
        }

        @Test
        @DisplayName("탈퇴 회원은 신규 가입 처리 — findByProviderAndProviderIdAndDeletedAtIsNull은 empty 반환")
        void withdrawnUser_treatedAsNewUser() {
            // 탈퇴 회원은 findByProviderAndProviderIdAndDeletedAtIsNull에서 empty 반환
            when(naverApiClient.exchangeToken("code", "state")).thenReturn(tokenResponse());
            when(naverApiClient.getProfile(ACCESS_TOKEN))
                    .thenReturn(profileResponse("탈퇴유저", "withdrawn@naver.com", null));
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, PROVIDER_ID))
                    .thenReturn(Optional.empty()); // 탈퇴 회원 → empty
            when(userRepository.existsByNickname(any(String.class))).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 15L);
                return u;
            });

            NaverLoginResponse result = authService.naverLogin(new NaverLoginRequest("code", "state"));

            assertThat(result.isNewUser()).isTrue();
            verify(userRepository, times(1)).save(any());
        }
    }
}
