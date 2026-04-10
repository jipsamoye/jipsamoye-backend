package com.jipsamoye.backend.domain.follow.repository;

import com.jipsamoye.backend.domain.follow.entity.Follow;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerAndFollowing(User follower, User following);
    
    Page<Follow> findAllByFollowing(User following, Pageable pageable);

    Page<Follow> findAllByFollower(User follower, Pageable pageable);

    long countByFollowing(User following);

    long countByFollower(User follower);

    void deleteAllByFollowerOrFollowing(User follower, User following);
}
