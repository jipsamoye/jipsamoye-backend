package com.jipsamoye.backend.domain.user.service;

import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.dto.request.UserUpdateRequest;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.event.ProfileUpdatedEvent;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PetPostRepository petPostRepository;
    private final FollowRepository followRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public UserResponse getProfile(String nickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "탈퇴한 사용자입니다.");
        }

        long postCount = petPostRepository.countByUser(user);
        long followerCount = followRepository.countByFollowing(user);
        long followingCount = followRepository.countByFollower(user);
        RankingInfo ranking = calculateRanking(user.getId());

        return UserResponse.of(user, postCount, followerCount, followingCount,
                ranking.rank(), ranking.totalRanked());
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
        }

        user.updateProfile(request.nickname(), request.bio(), request.profileImageUrl(),
                request.coverImageUrl(), request.socialLinks());

        eventPublisher.publishEvent(new ProfileUpdatedEvent(
                user.getId(), user.getNickname(), user.getProfileImageUrl()));

        long postCount = petPostRepository.countByUser(user);
        long followerCount = followRepository.countByFollowing(user);
        long followingCount = followRepository.countByFollower(user);
        RankingInfo ranking = calculateRanking(user.getId());

        return UserResponse.of(user, postCount, followerCount, followingCount,
                ranking.rank(), ranking.totalRanked());
    }

    private RankingInfo calculateRanking(Long userId) {
        long totalLikes = petPostRepository.sumLikeCountByUserId(userId);
        long totalRanked = userRepository.countActiveUsersWithLikes();
        Long rank = totalLikes > 0
                ? userRepository.countActiveUsersWithMoreLikesThan(totalLikes) + 1
                : null;
        return new RankingInfo(rank, totalRanked);
    }

    private record RankingInfo(Long rank, Long totalRanked) {}

    @Override
    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    @Override
    public PageResponse<PetPostListResponse> getUserPosts(String nickname, int page, int size) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Page<PetPostListResponse> postPage = petPostRepository
                .findAllByUser(user, PageRequest.of(page, size))
                .map(PetPostListResponse::from);
        return PageResponse.from(postPage);
    }
}
