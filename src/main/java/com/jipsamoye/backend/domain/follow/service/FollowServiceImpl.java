package com.jipsamoye.backend.domain.follow.service;

import com.jipsamoye.backend.domain.follow.dto.response.FollowUserResponse;
import com.jipsamoye.backend.domain.follow.entity.Follow;
import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public boolean toggleFollow(String nickname, Long userId) {
        User following = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 자기 자신 팔로우 방지
        if (follower.getId().equals(following.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "자기 자신을 팔로우할 수 없습니다.");
        }

        Optional<Follow> existingFollow = followRepository.findByFollowerAndFollowing(follower, following);

        if (existingFollow.isPresent()) {
            followRepository.delete(existingFollow.get());
            return false;
        } else {
            followRepository.save(Follow.builder()
                    .follower(follower)
                    .following(following)
                    .build());
            return true;
        }
    }

    @Override
    public PageResponse<FollowUserResponse> getFollowers(String nickname, int page, int size) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Page<FollowUserResponse> followerPage = followRepository
                .findAllByFollowing(user, PageRequest.of(page, size))
                .map(follow -> FollowUserResponse.from(follow.getFollower()));
        return PageResponse.from(followerPage);
    }

    @Override
    public PageResponse<FollowUserResponse> getFollowing(String nickname, int page, int size) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Page<FollowUserResponse> followingPage = followRepository
                .findAllByFollower(user, PageRequest.of(page, size))
                .map(follow -> FollowUserResponse.from(follow.getFollowing()));
        return PageResponse.from(followingPage);
    }
}
