package com.jipsamoye.backend.domain.chat.service;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.entity.ChatMessage;
import com.jipsamoye.backend.domain.chat.repository.ChatMessageRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Override
    public List<ChatMessageResponse> getRecentMessages(int size) {
        List<ChatMessageResponse> messages = chatMessageRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        List<ChatMessageResponse> reversed = new java.util.ArrayList<>(messages);
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long userId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ChatMessage message = ChatMessage.builder()
                .sender(user)
                .content(content)
                .build();

        chatMessageRepository.save(message);
        return ChatMessageResponse.from(message);
    }
}
