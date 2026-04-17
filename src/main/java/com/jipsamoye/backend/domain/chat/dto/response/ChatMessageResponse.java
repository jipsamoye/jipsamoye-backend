package com.jipsamoye.backend.domain.chat.dto.response;

import com.jipsamoye.backend.domain.chat.entity.ChatMessage;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        String type,
        Long id,
        String nickname,
        String profileImageUrl,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                "CHAT_MESSAGE",
                message.getId(),
                message.getSender().getNickname(),
                message.getSender().getProfileImageUrl(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
