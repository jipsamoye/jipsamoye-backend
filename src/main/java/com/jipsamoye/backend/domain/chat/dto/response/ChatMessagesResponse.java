package com.jipsamoye.backend.domain.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatMessagesResponse {

    private List<ChatMessageResponse> messages;
    private boolean hasMore;
}
