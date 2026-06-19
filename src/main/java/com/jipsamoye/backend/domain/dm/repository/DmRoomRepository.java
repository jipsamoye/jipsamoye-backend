package com.jipsamoye.backend.domain.dm.repository;

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
            "ORDER BY r.updatedAt DESC")
    List<DmRoom> findAllByUser(@Param("user") User user);
}
