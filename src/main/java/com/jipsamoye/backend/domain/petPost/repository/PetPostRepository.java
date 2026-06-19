package com.jipsamoye.backend.domain.petPost.repository;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PetPostRepository extends JpaRepository<PetPost, Long> {

    /**
     * 커서 페이징 첫 페이지 — id DESC(IDENTITY 단조증가 = createdAt DESC와 동순서, 안정적 tiebreaker).
     * soft-delete는 @SQLRestriction("deleted_at IS NULL")로 자동 적용. size+1 조회로 hasNext 판정.
     */
    List<PetPost> findByOrderByIdDesc(Pageable pageable);

    /**
     * 커서 페이징 다음 페이지 — 마지막으로 받은 id보다 작은(=더 오래된) 게시글을 id DESC로 조회.
     */
    List<PetPost> findByIdLessThanOrderByIdDesc(Long id, Pageable pageable);

    Page<PetPost> findAllByUser(User user, Pageable pageable);

    List<PetPost> findAllByUser(User user);

    long countByUser(User user);

    @Query("SELECT p FROM PetPost p " +
            "WHERE p.createdAt >= :start AND p.createdAt < :end " +
            "ORDER BY p.likeCount DESC, p.id DESC")
    Page<PetPost> findRanking(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end,
                              Pageable pageable);

    @Modifying
    @Query("UPDATE PetPost p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.user = :user AND p.deletedAt IS NULL")
    void softDeleteAllByUser(@Param("user") User user);

    /**
     * 제목 FULLTEXT(ngram) 구문검색. 무한스크롤이므로 Slice 반환(size+1 조회로 hasNext 판정, COUNT 없음).
     * 네이티브 쿼리는 @SQLRestriction이 자동 적용되지 않으므로 deleted_at IS NULL을 직접 명시한다.
     * kw는 서비스 계층에서 BOOLEAN MODE 연산자 제거 후 "정제어" 형태(구문검색)로 감싸서 전달된다.
     */
    @Query(value = "SELECT * FROM pet_post WHERE MATCH(title) AGAINST(:kw IN BOOLEAN MODE) "
            + "AND deleted_at IS NULL ORDER BY created_at DESC, id DESC", nativeQuery = true)
    Slice<PetPost> searchByTitleFulltext(@Param("kw") String kw, Pageable pageable);

    @Query("SELECT p FROM PetPost p ORDER BY p.likeCount DESC")
    List<PetPost> findTop10ByLikeCount(Pageable pageable);

    @Modifying
    @Query("UPDATE PetPost p SET p.likeCount = p.likeCount + :value WHERE p.id = :id")
    void updateLikeCount(@Param("id") Long id, @Param("value") int value);

    @Modifying
    @Query("UPDATE PetPost p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
    void incrementCommentCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PetPost p SET p.commentCount = p.commentCount - 1 WHERE p.id = :id AND p.commentCount > 0")
    void decrementCommentCount(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(p.likeCount), 0) FROM PetPost p WHERE p.user.id = :userId")
    long sumLikeCountByUserId(@Param("userId") Long userId);

    @Query("SELECT p.user.id, COALESCE(SUM(p.likeCount), 0) " +
           "FROM PetPost p WHERE p.user.id IN :userIds GROUP BY p.user.id")
    List<Object[]> sumLikeCountGroupedByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("SELECT p FROM PetPost p " +
           "WHERE p.user.id IN (SELECT f.following.id FROM Follow f WHERE f.follower.id = :followerId) " +
           "ORDER BY p.createdAt DESC, p.id DESC")
    Page<PetPost> findFeedByFollowerId(@Param("followerId") Long followerId, Pageable pageable);
}
