package com.jipsamoye.backend.domain.follow.repository;

import com.jipsamoye.backend.domain.follow.entity.Follow;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerAndFollowing(User follower, User following);

    /**
     * 주어진 닉네임 집합 중, 현재 유저(:meId)가 팔로우 중인 닉네임만 한 번의 쿼리로 조회한다 (isFollowing batch 매핑용, N+1 방지).
     */
    @Query("SELECT f.following.nickname FROM Follow f " +
            "WHERE f.follower.id = :meId AND f.following.nickname IN :nicknames")
    Set<String> findFollowingNicknamesIn(@Param("meId") Long meId,
                                         @Param("nicknames") Collection<String> nicknames);

    boolean existsByFollowerAndFollowing(User follower, User following);

    Page<Follow> findAllByFollowing(User following, Pageable pageable);

    Page<Follow> findAllByFollower(User follower, Pageable pageable);

    long countByFollowing(User following);

    long countByFollower(User follower);

    @Modifying
    @Query("DELETE FROM Follow f WHERE f.follower = :user OR f.following = :user")
    void deleteAllByUser(User user);
}
