package com.jipsamoye.backend.domain.dm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DmRoomResponse {

    private Long roomId;
    private Long otherUserId;
    private String otherUserNickname;
    private String otherUserProfileImageUrl;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
}
