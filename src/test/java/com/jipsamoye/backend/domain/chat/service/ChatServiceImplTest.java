package com.jipsamoye.backend.domain.chat.service;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.entity.ChatMessage;
import com.jipsamoye.backend.domain.chat.repository.ChatMessageRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.exception.BusinessException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @InjectMocks
    private ChatServiceImpl chatService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("메시지 전송 - 성공, 랜덤 닉네임 생성")
    void sendMessage_success() {
        Long userId = 1L;
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        chatService.sendMessage(userId, "안녕하세요");

        verify(chatMessageRepository).save(argThat(msg ->
                msg.getContent().equals("안녕하세요") &&
                msg.getAnonymousNickname() != null &&
                msg.getAnonymousNickname().startsWith("익명의 ")));
    }

    @Test
    @DisplayName("메시지 전송 - 같은 유저는 같은 닉네임 유지")
    void sendMessage_sameNicknameForSameUser() {
        Long userId = 1L;
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        chatService.sendMessage(userId, "첫 번째");
        chatService.sendMessage(userId, "두 번째");

        var nicknames = new java.util.ArrayList<String>();
        verify(chatMessageRepository, times(2)).save(argThat(msg -> {
            nicknames.add(msg.getAnonymousNickname());
            return true;
        }));
        assertThat(nicknames.get(0)).isEqualTo(nicknames.get(1));
    }

    @Test
    @DisplayName("메시지 전송 - 존재하지 않는 유저")
    void sendMessage_userNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(999L, "테스트"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("최근 메시지 조회 - 시간순 정렬")
    void getRecentMessages_orderedByTime() {
        ChatMessage msg1 = mock(ChatMessage.class);
        when(msg1.getId()).thenReturn(1L);
        when(msg1.getContent()).thenReturn("첫 번째");
        when(msg1.getAnonymousNickname()).thenReturn("익명의 멍집사1");
        when(msg1.getCreatedAt()).thenReturn(LocalDateTime.now().minusMinutes(1));

        ChatMessage msg2 = mock(ChatMessage.class);
        when(msg2.getId()).thenReturn(2L);
        when(msg2.getContent()).thenReturn("두 번째");
        when(msg2.getAnonymousNickname()).thenReturn("익명의 냥집사2");
        when(msg2.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(chatMessageRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)))
                .thenReturn(List.of(msg2, msg1));

        List<ChatMessageResponse> result = chatService.getRecentMessages(50);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("첫 번째");
        assertThat(result.get(1).getContent()).isEqualTo("두 번째");
    }
}
