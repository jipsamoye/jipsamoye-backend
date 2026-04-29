package com.jipsamoye.backend.domain.notification.entity;

import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private Long targetId;

    @Column
    private Long relatedPostId;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean isRead = false;

    @Builder
    public Notification(User receiver, User sender, NotificationType type,
                        Long targetId, Long relatedPostId, String message) {
        this.receiver = receiver;
        this.sender = sender;
        this.type = type;
        this.targetId = targetId;
        this.relatedPostId = relatedPostId;
        this.message = message;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
