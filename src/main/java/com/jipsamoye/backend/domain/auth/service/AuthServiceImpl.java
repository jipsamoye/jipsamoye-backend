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
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.util.SessionHintCookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Cookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final int NICKNAME_MAX_LENGTH = 10;
    private static final int NICKNAME_RETRY_LIMIT = 5;

    private final UserRepository userRepository;
    private final PetPostRepository petPostRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final HttpSession httpSession;
    private final HttpServletResponse httpServletResponse;
    private final ServerProperties serverProperties;
    private final NaverApiClient naverApiClient;

    @Override
    @Transactional
    public UserResponse createGuest() {
        String guestNickname = "손님" + UUID.randomUUID().toString().substring(0, 6);
        String guestId = UUID.randomUUID().toString();

        User guest = User.builder()
                .nickname(guestNickname)
                .email("guest_" + guestId + "@jipsamoye.com")
                .provider(Provider.GUEST)
                .providerId(guestId)
                .role(Role.GUEST)
                .build();

        User saved = userRepository.save(guest);
        issueSession(saved);

        RankingResult ranking = calculateRanking(saved.getId());
        return UserResponse.of(saved, 0, 0, 0, ranking.totalLikeCount(), ranking.ranking());
    }

    @Override
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long postCount = petPostRepository.countByUser(user);
        long followerCount = followRepository.countByFollowing(user);
        long followingCount = followRepository.countByFollower(user);
        RankingResult ranking = calculateRanking(user.getId());

        return UserResponse.of(user, postCount, followerCount, followingCount,
                ranking.totalLikeCount(), ranking.ranking());
    }

    @Override
    @Transactional
    public NaverLoginResponse naverLogin(NaverLoginRequest request) {
        // 1. 인가 코드로 액세스 토큰 교환
        NaverTokenResponse tokenResponse = naverApiClient.exchangeToken(request.code(), request.state());

        // 2. 액세스 토큰으로 프로필 조회
        NaverProfileResponse profileResponse = naverApiClient.getProfile(tokenResponse.accessToken());
        NaverProfileResponse.NaverProfile profile = profileResponse.response();

        // 3. 기존 회원 조회 (탈퇴 회원 제외)
        Optional<User> existingUser =
                userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(Provider.NAVER, profile.id());

        boolean isNewUser = existingUser.isEmpty();
        User user;

        if (isNewUser) {
            // 4. 신규 가입 처리
            String nickname = resolveNickname(profile.nickname());
            String email = (profile.email() != null && !profile.email().isBlank())
                    ? profile.email()
                    : "naver_" + profile.id() + "@jipsamoye.com";

            user = User.builder()
                    .nickname(nickname)
                    .email(email)
                    .provider(Provider.NAVER)
                    .providerId(profile.id())
                    .role(Role.USER)
                    .profileImageUrl(profile.profileImage())
                    .build();

            user = userRepository.save(user);
        } else {
            user = existingUser.get();
        }

        // 5. 세션 발급
        issueSession(user);

        // 6. 랭킹 계산 후 응답 반환
        RankingResult ranking = calculateRanking(user.getId());
        long postCount = isNewUser ? 0 : petPostRepository.countByUser(user);
        long followerCount = isNewUser ? 0 : followRepository.countByFollowing(user);
        long followingCount = isNewUser ? 0 : followRepository.countByFollower(user);
        UserResponse userResponse = UserResponse.of(user, postCount, followerCount, followingCount,
                ranking.totalLikeCount(), ranking.ranking());
        return NaverLoginResponse.of(isNewUser, userResponse);
    }

    /**
     * 네이버 닉네임으로부터 사용 가능한 닉네임을 결정한다.
     * - 10자 초과 시 truncate
     * - null·중복이면 "집사" + UUID 6자리로 자동 생성 (최대 5회 재시도)
     */
    private String resolveNickname(String naverNickname) {
        if (naverNickname != null && !naverNickname.isBlank()) {
            String truncated = naverNickname.length() > NICKNAME_MAX_LENGTH
                    ? naverNickname.substring(0, NICKNAME_MAX_LENGTH)
                    : naverNickname;
            if (!userRepository.existsByNickname(truncated)) {
                return truncated;
            }
        }

        for (int i = 0; i < NICKNAME_RETRY_LIMIT; i++) {
            String candidate = "집사" + UUID.randomUUID().toString().substring(0, 6);
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }

        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                "닉네임 자동 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    private RankingResult calculateRanking(Long userId) {
        long totalLikeCount = petPostRepository.sumLikeCountByUserId(userId);
        Long ranking = totalLikeCount > 0
                ? userRepository.countActiveUsersWithMoreLikesThan(totalLikeCount) + 1
                : null;
        return new RankingResult(totalLikeCount, ranking);
    }

    private record RankingResult(long totalLikeCount, Long ranking) {}

    /**
     * Spring Security SecurityContext에 인증 정보를 저장하고 세션 힌트 쿠키를 발급한다.
     * createGuest()와 naverLogin()에서 공통으로 사용한다.
     */
    private void issueSession(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user.getId(), user.getRole());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 세션에 SecurityContext 저장 (다음 요청에서 자동 복원)
        httpSession.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        // 프론트가 /api/auth/me 호출 여부를 판단할 수 있도록 힌트 쿠키 발급
        setSessionHintCookie();
    }

    @Override
    @Transactional
    public void logout() {
        SecurityContextHolder.clearContext();
        httpSession.invalidate();
        clearSessionHintCookie();
    }

    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        likeRepository.deleteAllByUser(user);
        followRepository.deleteAllByUser(user);
        commentRepository.softDeleteAllByUser(user);
        petPostRepository.softDeleteAllByUser(user);

        user.softDelete();

        SecurityContextHolder.clearContext();
        httpSession.invalidate();
        clearSessionHintCookie();
    }

    private void setSessionHintCookie() {
        Cookie cookieConfig = serverProperties.getServlet().getSession().getCookie();
        SessionHintCookie.set(httpServletResponse, cookieConfig.getDomain(), isSecure(cookieConfig));
    }

    private void clearSessionHintCookie() {
        Cookie cookieConfig = serverProperties.getServlet().getSession().getCookie();
        SessionHintCookie.clear(httpServletResponse, cookieConfig.getDomain(), isSecure(cookieConfig));
    }

    private static boolean isSecure(Cookie cookieConfig) {
        return Boolean.TRUE.equals(cookieConfig.getSecure());
    }
}
