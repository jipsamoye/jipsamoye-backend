package com.jipsamoye.backend.domain.auth.service;

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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PetPostRepository petPostRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final HttpSession httpSession;
    private final HttpServletResponse httpServletResponse;
    private final ServerProperties serverProperties;

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

        // Spring Security SecurityContext에 인증 정보 저장
        CustomUserDetails userDetails = new CustomUserDetails(saved.getId(), saved.getRole());
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

        return UserResponse.of(saved, 0, 0, 0, calculateRank(saved.getId()));
    }

    @Override
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long postCount = petPostRepository.countByUser(user);
        long followerCount = followRepository.countByFollowing(user);
        long followingCount = followRepository.countByFollower(user);
        Long rank = calculateRank(user.getId());

        return UserResponse.of(user, postCount, followerCount, followingCount, rank);
    }

    private Long calculateRank(Long userId) {
        long totalLikes = petPostRepository.sumLikeCountByUserId(userId);
        return totalLikes > 0
                ? userRepository.countActiveUsersWithMoreLikesThan(totalLikes) + 1
                : null;
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
