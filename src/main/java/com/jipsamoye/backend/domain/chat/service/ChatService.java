package com.jipsamoye.backend.domain.chat.service;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.dto.response.ChatMessagesResponse;

public interface ChatService {
    ChatMessagesResponse getMessages(int size, Long beforeId);
    ChatMessageResponse sendMessage(Long userId, String content);
}
