package com.jipsamoye.backend.domain.chat.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatSendRequest {
    private Long userId;
    private String content;
}
