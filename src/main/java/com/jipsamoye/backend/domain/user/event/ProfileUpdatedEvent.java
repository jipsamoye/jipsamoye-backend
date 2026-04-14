package com.jipsamoye.backend.domain.user.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ProfileUpdatedEvent {

    private final Long userId;
    private final String nickname;
    private final String profileImageUrl;
}
