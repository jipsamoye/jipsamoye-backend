package com.jipsamoye.backend.domain.chat.event;

import com.jipsamoye.backend.domain.chat.dto.response.ChatProfileUpdatedMessage;
import com.jipsamoye.backend.domain.user.event.ProfileUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatProfileEventListenerTest {

    @InjectMocks
    private ChatProfileEventListener listener;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("프로필 변경 이벤트 수신 시 /sub/chat/room으로 타입 있는 PROFILE_UPDATED 메시지 전송")
    void handleProfileUpdate_sendsWebSocketMessage() {
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(1L, "새닉네임", "https://new-image.jpg");

        listener.handleProfileUpdate(event);

        ArgumentCaptor<ChatProfileUpdatedMessage> captor =
                ArgumentCaptor.forClass(ChatProfileUpdatedMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/room"), captor.capture());

        ChatProfileUpdatedMessage payload = captor.getValue();
        assertThat(payload.type()).isEqualTo("PROFILE_UPDATED");
        assertThat(payload.nickname()).isEqualTo("새닉네임");
        assertThat(payload.profileImageUrl()).isEqualTo("https://new-image.jpg");
    }

    @Test
    @DisplayName("프로필 이미지가 null이면 빈 문자열로 전송")
    void handleProfileUpdate_nullProfileImage() {
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(1L, "닉네임", null);

        listener.handleProfileUpdate(event);

        ArgumentCaptor<ChatProfileUpdatedMessage> captor =
                ArgumentCaptor.forClass(ChatProfileUpdatedMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/room"), captor.capture());

        assertThat(captor.getValue().profileImageUrl()).isEqualTo("");
    }
}
