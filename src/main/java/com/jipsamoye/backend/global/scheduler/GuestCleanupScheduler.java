package com.jipsamoye.backend.global.scheduler;

import com.jipsamoye.backend.domain.comment.repository.CommentRepository;
import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.like.repository.LikeRepository;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserRepository userRepository;
    private final PetPostRepository petPostRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final FollowRepository followRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredGuests() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusHours(24);
        List<User> expiredGuests = userRepository.findAllByRoleAndCreatedAtBefore(Role.GUEST, expiredBefore);

        if (expiredGuests.isEmpty()) {
            log.info("만료된 게스트 유저 없음");
            return;
        }

        log.info("만료된 게스트 유저 정리 시작: {}명", expiredGuests.size());

        for (User guest : expiredGuests) {
            List<PetPost> guestPosts = petPostRepository.findAllByUser(guest);
            for (PetPost post : guestPosts) {
                likeRepository.deleteAllByPetPost(post);
                commentRepository.deleteAllByPetPost(post);
            }
            petPostRepository.deleteAll(guestPosts);
            followRepository.deleteAllByFollowerOrFollowing(guest, guest);
            // TODO: S3 이미지 삭제 추가
            userRepository.delete(guest);
        }

        log.info("만료된 게스트 유저 정리 완료: {}명 삭제", expiredGuests.size());
    }
}
