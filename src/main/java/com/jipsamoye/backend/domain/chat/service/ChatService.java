package com.jipsamoye.backend.domain.chat.service;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;

import java.util.List;

public interface ChatService {
    List<ChatMessageResponse> getRecentMessages(int size);
    ChatMessageResponse sendMessage(Long userId, String content);
}
