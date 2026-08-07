package com.jipsamoye.backend.domain.dm.repository;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomProjection;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

    @Query("SELECT r FROM DmRoom r WHERE (r.user1 = :u1 AND r.user2 = :u2) OR (r.user1 = :u2 AND r.user2 = :u1)")
    Optional<DmRoom> findByUsers(@Param("u1") User u1, @Param("u2") User u2);

    /**
     * 방 참여자 여부를 ID 기반 단일 쿼리로 확인한다.
     * DmRoom.isParticipant(userId)와 동일한 논리(user1.id 또는 user2.id == userId)를
     * lazy 연관 로딩 없이 수행한다. STOMP ChannelInterceptor.preSend처럼
     * 트랜잭션/영속성 컨텍스트 밖에서 호출되는 구독 인가 경로에서 사용한다.
     */
    @Query("SELECT COUNT(r) > 0 FROM DmRoom r " +
            "WHERE r.id = :roomId AND (r.user1.id = :userId OR r.user2.id = :userId)")
    boolean existsByIdAndParticipant(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("SELECT r FROM DmRoom r WHERE (r.user1 = :user OR r.user2 = :user) " +
            "AND EXISTS (SELECT 1 FROM DmMessage m WHERE m.room = r) " +
            "ORDER BY (SELECT MAX(om.id) FROM DmMessage om WHERE om.room = r) DESC")
    List<DmRoom> findAllByUser(@Param("user") User user);

    /**
     * 방 목록을 단일 쿼리로 가져온다(N+1 제거). 각 방마다 마지막 메시지(내용·시각)와 unread count,
     * 상대방 닉네임·프로필 이미지를 상관 서브쿼리와 CASE로 한 번에 계산한다.
     *
     * <p>{@code findAllByUser}와 동일한 의미를 보존한다:
     * <ul>
     *   <li>참여 방 필터: {@code user1.id = :userId OR user2.id = :userId}</li>
     *   <li>빈 방 제외: {@code EXISTS (message)}</li>
     *   <li>정렬: 마지막 메시지 {@code MAX(id) DESC} — 최근 대화 순</li>
     * </ul>
     *
     * <p>마지막 메시지는 {@code MAX(id)}로 식별한다(IDENTITY 단조 증가 → createdAt 타이에도 모호하지 않음).
     */
    @Query("SELECT new com.jipsamoye.backend.domain.dm.dto.response.DmRoomProjection("
            + "r.id, "
            + "CASE WHEN r.user1.id = :userId THEN r.user2.nickname ELSE r.user1.nickname END, "
            + "CASE WHEN r.user1.id = :userId THEN r.user2.profileImageUrl ELSE r.user1.profileImageUrl END, "
            + "(SELECT lm.content FROM DmMessage lm "
            + "  WHERE lm.id = (SELECT MAX(m2.id) FROM DmMessage m2 WHERE m2.room = r)), "
            + "(SELECT lm.createdAt FROM DmMessage lm "
            + "  WHERE lm.id = (SELECT MAX(m3.id) FROM DmMessage m3 WHERE m3.room = r)), "
            + "(SELECT COUNT(u) FROM DmMessage u "
            + "  WHERE u.room = r AND u.sender.id <> :userId AND u.readAt IS NULL)) "
            + "FROM DmRoom r "
            + "WHERE (r.user1.id = :userId OR r.user2.id = :userId) "
            + "AND EXISTS (SELECT 1 FROM DmMessage m WHERE m.room = r) "
            + "ORDER BY (SELECT MAX(om.id) FROM DmMessage om WHERE om.room = r) DESC")
    List<DmRoomProjection> findRoomSummaries(@Param("userId") Long userId);
}
