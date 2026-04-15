package com.jipsamoye.backend.domain.chat.dto.response;

import com.jipsamoye.backend.domain.chat.entity.ChatMessage;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponse {

    private String type;
    private Long id;
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private String content;
    private LocalDateTime createdAt;

    public static ChatMessageResponse from(ChatMessage message, ImageCdnConverter converter) {
        return ChatMessageResponse.builder()
                .type("CHAT_MESSAGE")
                .id(message.getId())
                .userId(message.getSender().getId())
                .nickname(message.getSender().getNickname())
                .profileImageUrl(converter.toCdnUrl(message.getSender().getProfileImageUrl()))
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
