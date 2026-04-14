package com.jipsamoye.backend.domain.chat.dto.response;

import com.jipsamoye.backend.domain.chat.entity.ChatMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponse {

    private Long id;
    private String content;
    private String anonymousNickname;
    private LocalDateTime createdAt;

    public static ChatMessageResponse from(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .anonymousNickname(message.getAnonymousNickname())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
