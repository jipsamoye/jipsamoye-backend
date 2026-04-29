package com.jipsamoye.backend.domain.notification.event;

import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NotificationEvent {

    private final User receiver;
    private final User sender;
    private final NotificationType type;
    private final Long targetId;
    private final Long relatedPostId;
    private final String message;
}
