package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.event.DmMessagesReadEvent;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Mock
    private FollowRepository followRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Nested
    @DisplayName("createRoom 메서드")
    class CreateRoomTest {

        @Test
        @DisplayName("채팅방 생성 - 성공")
        void createRoom_success() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("냥집사");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));
            when(followRepository.existsByFollowerAndFollowing(user, target)).thenReturn(true);
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.empty());

            DmRoom savedRoom = mock(DmRoom.class);
            when(savedRoom.getId()).thenReturn(1L);
            when(dmRoomRepository.save(any(DmRoom.class))).thenReturn(savedRoom);

            DmRoomResponse response = dmService.createRoom(1L, "냥집사");

            assertThat(response.roomId()).isEqualTo(1L);
            assertThat(response.otherUserNickname()).isEqualTo("냥집사");
        }

        @Test
        @DisplayName("채팅방 생성 - 자기 자신에게 DM 불가")
        void createRoom_selfDm() {
            User user = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(user.getNickname()).thenReturn("멍집사");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("멍집사")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> dmService.createRoom(1L, "멍집사"))
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
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));
            when(followRepository.existsByFollowerAndFollowing(user, target)).thenReturn(true);

            DmRoom existingRoom = mock(DmRoom.class);
            when(existingRoom.getId()).thenReturn(5L);
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.of(existingRoom));

            DmRoomResponse response = dmService.createRoom(1L, "냥집사");

            assertThat(response.roomId()).isEqualTo(5L);
            verify(dmRoomRepository, never()).save(any(DmRoom.class));
        }

        @Test
        @DisplayName("채팅방 생성 - 미팔로우 상대에게는 FORBIDDEN 예외, dmRoomRepository.save 미호출")
        void createRoom_notFollowing_throwsForbidden() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("낯선유저")).thenReturn(Optional.of(target));
            when(followRepository.existsByFollowerAndFollowing(user, target)).thenReturn(false);

            assertThatThrownBy(() -> dmService.createRoom(1L, "낯선유저"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));
            verify(dmRoomRepository, never()).save(any(DmRoom.class));
        }
    }

    @Nested
    @DisplayName("sendMessage 메서드")
    class SendMessageTest {

        @Test
        @DisplayName("메시지 전송 - 참여자가 아니면 FORBIDDEN")
        void sendMessage_notParticipant() {
            DmRoom room = mock(DmRoom.class);
            when(room.isParticipant(3L)).thenReturn(false);
            when(dmRoomRepository.findById(1L)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> dmService.sendMessage(3L, 1L, "테스트", null))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("getMessages 메서드")
    class GetMessagesTest {

        @Test
        @DisplayName("메시지 조회 - 참여자가 아니면 FORBIDDEN")
        void getMessages_notParticipant() {
            DmRoom room = mock(DmRoom.class);
            when(room.isParticipant(3L)).thenReturn(false);
            when(dmRoomRepository.findById(1L)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> dmService.getMessages(1L, 3L, 0, 50))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("markAllAsRead가 1 이상 반환 시 eventPublisher.publishEvent 호출 - roomId/readerNickname 검증")
        void getMessages_markedRead_publishesEvent() {
            DmRoom room = mock(DmRoom.class);
            User user = mock(User.class);
            when(room.isParticipant(1L)).thenReturn(true);
            when(dmRoomRepository.findById(10L)).thenReturn(Optional.of(room));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(user.getNickname()).thenReturn("멍집사");
            when(dmMessageRepository.markAllAsRead(room, user)).thenReturn(3);
            when(dmMessageRepository.findAllByRoomOrderByCreatedAtDesc(eq(room), any()))
                    .thenReturn(Page.empty());

            dmService.getMessages(10L, 1L, 0, 50);

            ArgumentCaptor<DmMessagesReadEvent> captor = ArgumentCaptor.forClass(DmMessagesReadEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            DmMessagesReadEvent event = captor.getValue();
            assertThat(event.roomId()).isEqualTo(10L);
            assertThat(event.readerNickname()).isEqualTo("멍집사");
            assertThat(event.readAt()).isNotNull();
        }

        @Test
        @DisplayName("markAllAsRead가 0 반환 시 eventPublisher.publishEvent 미호출")
        void getMessages_nothingRead_noEvent() {
            DmRoom room = mock(DmRoom.class);
            User user = mock(User.class);
            when(room.isParticipant(1L)).thenReturn(true);
            when(dmRoomRepository.findById(10L)).thenReturn(Optional.of(room));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(dmMessageRepository.markAllAsRead(room, user)).thenReturn(0);
            when(dmMessageRepository.findAllByRoomOrderByCreatedAtDesc(eq(room), any()))
                    .thenReturn(Page.empty());

            dmService.getMessages(10L, 1L, 0, 50);

            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
