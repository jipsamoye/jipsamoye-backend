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

    @Query("SELECT r FROM DmRoom r WHERE r.user1 = :user OR r.user2 = :user ORDER BY r.updatedAt DESC")
    List<DmRoom> findAllByUser(@Param("user") User user);
}
