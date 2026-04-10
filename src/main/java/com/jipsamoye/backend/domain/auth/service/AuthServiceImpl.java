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
import com.jipsamoye.backend.global.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
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
        httpSession.setAttribute("userId", saved.getId());

        return UserResponse.of(saved, 0, 0, 0);
    }

    @Override
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long postCount = petPostRepository.countByUser(user);
        long followerCount = followRepository.countByFollowing(user);
        long followingCount = followRepository.countByFollower(user);
        return UserResponse.of(user, postCount, followerCount, followingCount);
    }

    @Override
    @Transactional
    public void logout() {
        httpSession.invalidate();
    }

    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Like hard delete → Follow hard delete → Comment soft delete → PetPost soft delete → User soft delete
        likeRepository.deleteAllByUser(user);
        followRepository.deleteAllByUser(user);
        commentRepository.softDeleteAllByUser(user);
        petPostRepository.softDeleteAllByUser(user);
        // TODO: S3 이미지 삭제 추가

        user.softDelete();
        httpSession.invalidate();
    }
}
