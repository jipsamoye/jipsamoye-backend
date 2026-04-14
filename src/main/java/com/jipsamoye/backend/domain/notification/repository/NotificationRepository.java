package com.jipsamoye.backend.domain.notification.repository;

import com.jipsamoye.backend.domain.notification.entity.Notification;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByReceiverOrderByCreatedAtDesc(User receiver, Pageable pageable);

    long countByReceiverAndIsReadFalse(User receiver);
}
