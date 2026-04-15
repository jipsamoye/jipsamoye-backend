package com.jipsamoye.backend.domain.chat.service;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessagesResponse;
import com.jipsamoye.backend.domain.chat.entity.ChatMessage;
import com.jipsamoye.backend.domain.chat.repository.ChatMessageRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @InjectMocks
    private ChatServiceImpl chatService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageCdnConverter imageCdnConverter;

    @Test
    @DisplayName("메시지 전송 - 성공")
    void sendMessage_success() {
        Long userId = 1L;
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        chatService.sendMessage(userId, "안녕하세요");

        verify(chatMessageRepository).save(argThat(msg ->
                msg.getContent().equals("안녕하세요") &&
                msg.getSender() == user));
    }

    @Test
    @DisplayName("메시지 전송 - 존재하지 않는 유저")
    void sendMessage_userNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(999L, "테스트"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("최신 메시지 조회 - beforeId 없으면 최신 메시지")
    void getMessages_latest() {
        User sender = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(sender.getNickname()).thenReturn("멍집사");

        ChatMessage msg1 = mock(ChatMessage.class);
        when(msg1.getId()).thenReturn(1L);
        when(msg1.getContent()).thenReturn("첫 번째");
        when(msg1.getSender()).thenReturn(sender);
        when(msg1.getCreatedAt()).thenReturn(LocalDateTime.now().minusMinutes(1));

        ChatMessage msg2 = mock(ChatMessage.class);
        when(msg2.getId()).thenReturn(2L);
        when(msg2.getContent()).thenReturn("두 번째");
        when(msg2.getSender()).thenReturn(sender);
        when(msg2.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(chatMessageRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 31)))
                .thenReturn(List.of(msg2, msg1));

        ChatMessagesResponse response = chatService.getMessages(30, null);

        assertThat(response.getMessages()).hasSize(2);
        assertThat(response.getMessages().get(0).getContent()).isEqualTo("첫 번째");
        assertThat(response.getMessages().get(1).getContent()).isEqualTo("두 번째");
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("이전 메시지 조회 - beforeId로 커서 페이지네이션")
    void getMessages_beforeId() {
        User sender = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(sender.getNickname()).thenReturn("멍집사");

        ChatMessage msg = mock(ChatMessage.class);
        when(msg.getId()).thenReturn(5L);
        when(msg.getContent()).thenReturn("이전 메시지");
        when(msg.getSender()).thenReturn(sender);
        when(msg.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(chatMessageRepository.findByIdLessThanOrderByCreatedAtDesc(10L, PageRequest.of(0, 31)))
                .thenReturn(List.of(msg));

        ChatMessagesResponse response = chatService.getMessages(30, 10L);

        assertThat(response.getMessages()).hasSize(1);
        assertThat(response.getMessages().get(0).getContent()).isEqualTo("이전 메시지");
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("hasMore - 더 불러올 메시지가 있으면 true")
    void getMessages_hasMore() {
        User sender = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(sender.getNickname()).thenReturn("멍집사");

        // size=2로 요청하면 3개를 조회 → 3개가 오면 hasMore=true
        List<ChatMessage> messages = new java.util.ArrayList<>();
        for (int i = 3; i >= 1; i--) {
            ChatMessage msg = mock(ChatMessage.class);
            lenient().when(msg.getId()).thenReturn((long) i);
            lenient().when(msg.getContent()).thenReturn("메시지" + i);
            lenient().when(msg.getSender()).thenReturn(sender);
            lenient().when(msg.getCreatedAt()).thenReturn(LocalDateTime.now());
            messages.add(msg);
        }

        when(chatMessageRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 3)))
                .thenReturn(messages);

        ChatMessagesResponse response = chatService.getMessages(2, null);

        assertThat(response.getMessages()).hasSize(2);
        assertThat(response.isHasMore()).isTrue();
    }
}
