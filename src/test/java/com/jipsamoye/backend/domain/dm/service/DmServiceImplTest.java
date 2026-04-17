package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DmServiceImplTest {

    @InjectMocks
    private DmServiceImpl dmService;

    @Mock
    private DmRoomRepository dmRoomRepository;

    @Mock
    private DmMessageRepository dmMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("채팅방 생성 - 성공")
    void createRoom_success() {
        User user = mock(User.class);
        User target = mock(User.class);
        lenient().when(user.getId()).thenReturn(1L);
        lenient().when(target.getId()).thenReturn(2L);
        when(target.getNickname()).thenReturn("냥집사");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.empty());

        DmRoom savedRoom = mock(DmRoom.class);
        when(savedRoom.getId()).thenReturn(1L);
        when(dmRoomRepository.save(any(DmRoom.class))).thenReturn(savedRoom);

        DmRoomResponse response = dmService.createRoom(1L, 2L);

        assertThat(response.roomId()).isEqualTo(1L);
        assertThat(response.otherUserNickname()).isEqualTo("냥집사");
    }

    @Test
    @DisplayName("채팅방 생성 - 자기 자신에게 DM 불가")
    void createRoom_selfDm() {
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> dmService.createRoom(1L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("채팅방 생성 - 이미 존재하면 기존 방 반환")
    void createRoom_existingRoom() {
        User user = mock(User.class);
        User target = mock(User.class);
        lenient().when(user.getId()).thenReturn(1L);
        lenient().when(target.getId()).thenReturn(2L);
        when(target.getNickname()).thenReturn("냥집사");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        DmRoom existingRoom = mock(DmRoom.class);
        when(existingRoom.getId()).thenReturn(5L);
        when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.of(existingRoom));

        DmRoomResponse response = dmService.createRoom(1L, 2L);

        assertThat(response.roomId()).isEqualTo(5L);
        verify(dmRoomRepository, never()).save(any(DmRoom.class));
    }

    @Test
    @DisplayName("메시지 전송 - 참여자가 아니면 FORBIDDEN")
    void sendMessage_notParticipant() {
        DmRoom room = mock(DmRoom.class);
        when(room.isParticipant(3L)).thenReturn(false);
        when(dmRoomRepository.findById(1L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> dmService.sendMessage(3L, 1L, "테스트", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("메시지 조회 - 참여자가 아니면 FORBIDDEN")
    void getMessages_notParticipant() {
        DmRoom room = mock(DmRoom.class);
        when(room.isParticipant(3L)).thenReturn(false);
        when(dmRoomRepository.findById(1L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> dmService.getMessages(1L, 3L, 0, 50))
                .isInstanceOf(BusinessException.class);
    }
}
