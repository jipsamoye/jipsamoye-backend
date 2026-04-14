package com.jipsamoye.backend.domain.dm.repository;

import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

    Page<DmMessage> findAllByRoomOrderByCreatedAtDesc(DmRoom room, Pageable pageable);

    Optional<DmMessage> findFirstByRoomOrderByCreatedAtDesc(DmRoom room);

    @Query("SELECT COUNT(m) FROM DmMessage m WHERE m.room = :room AND m.sender != :user AND m.readAt IS NULL")
    long countUnread(@Param("room") DmRoom room, @Param("user") User user);

    @Modifying
    @Query("UPDATE DmMessage m SET m.readAt = CURRENT_TIMESTAMP WHERE m.room = :room AND m.sender != :user AND m.readAt IS NULL")
    void markAllAsRead(@Param("room") DmRoom room, @Param("user") User user);
}
