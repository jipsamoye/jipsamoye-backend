package com.jipsamoye.backend.domain.chat.dto.response;

import java.util.List;

public record ChatMessagesResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore
) {
}
