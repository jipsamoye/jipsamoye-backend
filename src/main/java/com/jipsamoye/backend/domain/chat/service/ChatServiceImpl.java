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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    private final ConcurrentHashMap<Long, String> nicknameCache = new ConcurrentHashMap<>();
    private static final String[] NICKNAME_PREFIXES = {"멍집사", "냥집사", "댕댕이맘", "산책러", "간식요정"};
    private final Random random = new Random();

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

        String nickname = nicknameCache.computeIfAbsent(userId,
                id -> "익명의 " + NICKNAME_PREFIXES[random.nextInt(NICKNAME_PREFIXES.length)] + random.nextInt(99));

        ChatMessage message = ChatMessage.builder()
                .sender(user)
                .content(content)
                .anonymousNickname(nickname)
                .build();

        chatMessageRepository.save(message);
        return ChatMessageResponse.from(message);
    }
}
