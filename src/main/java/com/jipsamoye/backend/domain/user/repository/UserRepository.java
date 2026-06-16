package com.jipsamoye.backend.domain.user.repository;

import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(Provider provider, String providerId);

    boolean existsByNickname(String nickname);

    Optional<User> findByNicknameAndDeletedAtIsNull(String nickname);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    List<User> findAllByRoleAndDeletedAtIsNullAndCreatedAtBefore(Role role, LocalDateTime before);

    /**
     * 닉네임 부분일치 검색 (대소문자 무시).
     * - 탈퇴 유저 제외(deletedAt IS NULL), 본인(:meId) 제외
     * - 정렬: 정확 일치(0) > 접두 일치(1) > 부분 일치(2), 동순위는 닉네임 ASC
     * - 비로그인은 매칭되지 않는 sentinel meId(예: -1)를 넘기면 아무도 제외되지 않는다.
     * - LIKE 와일드카드 안전: {@code qLike}는 서비스에서 '%','_','\'를 이스케이프한 값을 받아
     *   {@code ESCAPE '\'}로 literal 매칭한다. 정확 일치(=) 비교는 이스케이프하지 않은
     *   원본 {@code qExact}로 수행한다(이스케이프 문자가 = 비교에 끼면 안 되므로 분리).
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND u.id <> :meId
              AND LOWER(u.nickname) LIKE LOWER(CONCAT('%', :qLike, '%')) ESCAPE '\\'
            ORDER BY
              CASE
                WHEN LOWER(u.nickname) = LOWER(:qExact) THEN 0
                WHEN LOWER(u.nickname) LIKE LOWER(CONCAT(:qLike, '%')) ESCAPE '\\' THEN 1
                ELSE 2
              END,
              u.nickname ASC
            """)
    Page<User> searchByNickname(@Param("qLike") String qLike, @Param("qExact") String qExact,
                                @Param("meId") Long meId, Pageable pageable);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT u.id, COALESCE(SUM(p.like_count), 0) AS total
                FROM users u
                LEFT JOIN pet_post p ON p.user_id = u.id
                WHERE u.deleted_at IS NULL
                GROUP BY u.id
            ) t WHERE t.total > :myTotal
            """, nativeQuery = true)
    long countActiveUsersWithMoreLikesThan(@Param("myTotal") long myTotal);
}
