package com.jipsamoye.backend.domain.dm.event;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DmRoomUpdatedEventListener 단위 테스트")
class DmRoomUpdatedEventListenerTest {

    @InjectMocks
    private DmRoomUpdatedEventListener listener;

    @Mock
    private DmRoomRepository dmRoomRepository;

    @Mock
    private DmMessageRepository dmMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("각 수신자 기준으로 상대방/unread/lastMessage를 계산해 '/sub/dm/rooms'로 전송")
    void handle_sendsPerRecipientRoomResponse() {
        User user1 = mock(User.class);
        User user2 = mock(User.class);
        when(user1.getId()).thenReturn(1L);
        lenient().when(user2.getId()).thenReturn(2L);
        when(user1.getNickname()).thenReturn("멍집사");
        when(user2.getNickname()).thenReturn("냥집사");
        when(user1.getProfileImageUrl()).thenReturn("img1");
        when(user2.getProfileImageUrl()).thenReturn("img2");

        DmRoom room = mock(DmRoom.class);
        when(room.getId()).thenReturn(10L);
        when(room.getUser1()).thenReturn(user1);
        when(room.getUser2()).thenReturn(user2);
        when(room.isParticipant(1L)).thenReturn(true);
        when(room.isParticipant(2L)).thenReturn(true);

        DmMessage lastMsg = mock(DmMessage.class);
        when(lastMsg.getContent()).thenReturn("마지막");
        when(dmRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(room)).thenReturn(Optional.of(lastMsg));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(dmMessageRepository.countUnread(room, user1)).thenReturn(0L);
        when(dmMessageRepository.countUnread(room, user2)).thenReturn(3L);

        listener.handle(new DmRoomUpdatedEvent(10L, List.of(1L, 2L)));

        // user1 기준: 상대방 = user2(냥집사), unread = 0
        ArgumentCaptor<DmRoomResponse> captor1 = ArgumentCaptor.forClass(DmRoomResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq("/sub/dm/rooms"), captor1.capture());
        DmRoomResponse r1 = captor1.getValue();
        assertThat(r1.roomId()).isEqualTo(10L);
        assertThat(r1.otherUserNickname()).isEqualTo("냥집사");
        assertThat(r1.otherUserProfileImageUrl()).isEqualTo("img2");
        assertThat(r1.lastMessage()).isEqualTo("마지막");
        assertThat(r1.unreadCount()).isEqualTo(0L);

        // user2 기준: 상대방 = user1(멍집사), unread = 3
        ArgumentCaptor<DmRoomResponse> captor2 = ArgumentCaptor.forClass(DmRoomResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq("/sub/dm/rooms"), captor2.capture());
        DmRoomResponse r2 = captor2.getValue();
        assertThat(r2.otherUserNickname()).isEqualTo("멍집사");
        assertThat(r2.unreadCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("방이 존재하지 않으면 아무 것도 전송하지 않음")
    void handle_roomMissing_noSend() {
        when(dmRoomRepository.findById(99L)).thenReturn(Optional.empty());

        listener.handle(new DmRoomUpdatedEvent(99L, List.of(1L)));

        verifyNoInteractions(messagingTemplate);
    }
}
