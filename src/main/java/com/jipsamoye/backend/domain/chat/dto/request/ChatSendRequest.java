package com.jipsamoye.backend.domain.chat.dto.request;

public record ChatSendRequest(
        Long userId,
        String content
) {
}
