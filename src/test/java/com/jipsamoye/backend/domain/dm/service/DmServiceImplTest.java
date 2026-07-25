package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomProjection;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.event.DmMessagesReadEvent;
import com.jipsamoye.backend.domain.dm.event.DmRoomUpdatedEvent;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
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

import java.time.LocalDateTime;
import java.util.List;
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
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DmRoomResolver dmRoomResolver;

    @Nested
    @DisplayName("getRooms 메서드")
    class GetRoomsTest {

        @Test
        @DisplayName("존재하지 않는 유저면 USER_NOT_FOUND")
        void getRooms_userNotFound() {
            when(userRepository.existsById(1L)).thenReturn(false);

            assertThatThrownBy(() -> dmService.getRooms(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
            verify(dmRoomRepository, never()).findRoomSummaries(any());
        }

        @Test
        @DisplayName("프로젝션 결과를 매퍼로 DmRoomResponse 리스트로 매핑(순서·필드 보존)")
        void getRooms_mapsProjections() {
            LocalDateTime t1 = LocalDateTime.of(2026, 6, 19, 10, 0);
            LocalDateTime t2 = LocalDateTime.of(2026, 6, 19, 9, 0);
            when(userRepository.existsById(1L)).thenReturn(true);
            when(dmRoomRepository.findRoomSummaries(1L)).thenReturn(List.of(
                    new DmRoomProjection(10L, "상대A", "imgA", "마지막A", t1, 3L),
                    new DmRoomProjection(20L, "상대B", null, null, t2, 0L)
            ));

            List<DmRoomResponse> rooms = dmService.getRooms(1L);

            assertThat(rooms).hasSize(2);
            assertThat(rooms.get(0).roomId()).isEqualTo(10L);
            assertThat(rooms.get(0).otherUserNickname()).isEqualTo("상대A");
            assertThat(rooms.get(0).otherUserProfileImageUrl()).isEqualTo("imgA");
            assertThat(rooms.get(0).lastMessage()).isEqualTo("마지막A");
            assertThat(rooms.get(0).lastMessageAt()).isEqualTo(t1);
            assertThat(rooms.get(0).unreadCount()).isEqualTo(3L);
            assertThat(rooms.get(1).roomId()).isEqualTo(20L);
            assertThat(rooms.get(1).otherUserProfileImageUrl()).isNull();
            assertThat(rooms.get(1).lastMessage()).isNull();
            assertThat(rooms.get(1).unreadCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("방이 없으면 빈 리스트")
        void getRooms_empty() {
            when(userRepository.existsById(1L)).thenReturn(true);
            when(dmRoomRepository.findRoomSummaries(1L)).thenReturn(List.of());

            assertThat(dmService.getRooms(1L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("createRoom 메서드 (resolve)")
    class CreateRoomTest {

        @Test
        @DisplayName("기존 방에 메시지가 있으면 - roomId 포함 응답 반환, save 미호출")
        void createRoom_existingRoomWithMessage_returnsRoomId() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("냥집사");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));

            DmRoom existingRoom = mock(DmRoom.class);
            when(existingRoom.getId()).thenReturn(5L);
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.of(existingRoom));

            DmMessage lastMsg = mock(DmMessage.class);
            when(lastMsg.getContent()).thenReturn("마지막 메시지");
            when(dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(existingRoom))
                    .thenReturn(Optional.of(lastMsg));
            when(dmMessageRepository.countUnread(existingRoom, user)).thenReturn(2L);

            DmRoomResponse response = dmService.createRoom(1L, "냥집사", false);

            assertThat(response.roomId()).isEqualTo(5L);
            assertThat(response.otherUserNickname()).isEqualTo("냥집사");
            assertThat(response.lastMessage()).isEqualTo("마지막 메시지");
            assertThat(response.unreadCount()).isEqualTo(2L);
            verify(dmRoomRepository, never()).save(any(DmRoom.class));
        }

        @Test
        @DisplayName("기존 빈 방(메시지 없음)이면 - draft 응답(roomId null) 반환, save 미호출")
        void createRoom_existingEmptyRoom_returnsDraft() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("냥집사");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));

            DmRoom existingRoom = mock(DmRoom.class);
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.of(existingRoom));
            when(dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(existingRoom))
                    .thenReturn(Optional.empty());

            DmRoomResponse response = dmService.createRoom(1L, "냥집사", false);

            assertThat(response.roomId()).isNull();
            assertThat(response.otherUserNickname()).isEqualTo("냥집사");
            verify(dmRoomRepository, never()).save(any(DmRoom.class));
        }

        @Test
        @DisplayName("방이 없으면 - draft 응답(roomId null) 반환, save 미호출")
        void createRoom_noRoom_returnsDraft() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("냥집사");
            when(target.getProfileImageUrl()).thenReturn("http://img");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.empty());

            DmRoomResponse response = dmService.createRoom(1L, "냥집사", false);

            assertThat(response.roomId()).isNull();
            assertThat(response.otherUserNickname()).isEqualTo("냥집사");
            assertThat(response.otherUserProfileImageUrl()).isEqualTo("http://img");
            verify(dmRoomRepository, never()).save(any(DmRoom.class));
            verify(dmMessageRepository, never()).findFirstByRoomOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("자기 자신에게 DM 불가")
        void createRoom_selfDm() {
            User user = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("멍집사")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> dmService.createRoom(1L, "멍집사", false))
                    .isInstanceOf(BusinessException.class);
            verify(dmRoomRepository, never()).save(any(DmRoom.class));
        }

        @Test
        @DisplayName("미팔로우 상대도 DM 가능 - 방이 없으면 draft 응답 반환, save 미호출")
        void createRoom_notFollowing_returnsDraft() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("낯선유저");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("낯선유저")).thenReturn(Optional.of(target));
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.empty());

            DmRoomResponse response = dmService.createRoom(1L, "낯선유저", false);

            assertThat(response.roomId()).isNull();
            assertThat(response.otherUserNickname()).isEqualTo("낯선유저");
            verify(dmRoomRepository, never()).save(any(DmRoom.class));
        }

        @Test
        @DisplayName("create=true & 방 없음 - resolver로 방을 생성하고 roomId 반환")
        void createRoom_createTrue_noRoom_createsAndReturnsRoomId() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("냥집사");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.empty());
            when(dmRoomResolver.resolveOrCreateRoomId(user, target)).thenReturn(77L);

            DmRoomResponse res = dmService.createRoom(1L, "냥집사", true);

            assertThat(res.roomId()).isEqualTo(77L);
            assertThat(res.lastMessage()).isNull();
            assertThat(res.unreadCount()).isEqualTo(0L);
            verify(dmRoomResolver).resolveOrCreateRoomId(user, target);
        }

        @Test
        @DisplayName("create=true & 빈 방 존재 - 기존 방 roomId 재사용, resolver 미호출")
        void createRoom_createTrue_emptyRoomExists_reusesExisting() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("냥집사");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));
            DmRoom emptyRoom = mock(DmRoom.class);
            when(emptyRoom.getId()).thenReturn(55L);
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.of(emptyRoom));
            when(dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(emptyRoom))
                    .thenReturn(Optional.empty());

            DmRoomResponse res = dmService.createRoom(1L, "냥집사", true);

            assertThat(res.roomId()).isEqualTo(55L);
            assertThat(res.lastMessage()).isNull();
            verify(dmRoomResolver, never()).resolveOrCreateRoomId(any(), any());
        }

        @Test
        @DisplayName("create=true & 메시지 있는 방 - 기존 방 응답 그대로, resolver 미호출")
        void createRoom_createTrue_roomWithMessage_returnsExisting() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("냥집사");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));
            DmRoom room = mock(DmRoom.class);
            when(room.getId()).thenReturn(10L);
            DmMessage lastMsg = mock(DmMessage.class);
            when(lastMsg.getContent()).thenReturn("마지막 메시지");
            when(lastMsg.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 7, 25, 12, 0));
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.of(room));
            when(dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(room))
                    .thenReturn(Optional.of(lastMsg));
            when(dmMessageRepository.countUnread(room, user)).thenReturn(2L);

            DmRoomResponse res = dmService.createRoom(1L, "냥집사", true);

            assertThat(res.roomId()).isEqualTo(10L);
            assertThat(res.lastMessage()).isEqualTo("마지막 메시지");
            assertThat(res.unreadCount()).isEqualTo(2L);
            verify(dmRoomResolver, never()).resolveOrCreateRoomId(any(), any());
        }

        @Test
        @DisplayName("create=false & 방 없음 - 기존 draft 동작 보존 (roomId=null, 저장 없음)")
        void createRoom_createFalse_noRoom_returnsDraft() {
            User user = mock(User.class);
            User target = mock(User.class);
            lenient().when(user.getId()).thenReturn(1L);
            lenient().when(target.getId()).thenReturn(2L);
            when(target.getNickname()).thenReturn("냥집사");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));
            when(dmRoomRepository.findByUsers(user, target)).thenReturn(Optional.empty());

            DmRoomResponse res = dmService.createRoom(1L, "냥집사", false);

            assertThat(res.roomId()).isNull();
            verify(dmRoomResolver, never()).resolveOrCreateRoomId(any(), any());
            verify(dmRoomRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("sendMessage 메서드")
    class SendMessageTest {

        @Test
        @DisplayName("roomId 지정 - 참여자가 아니면 FORBIDDEN")
        void sendMessage_notParticipant() {
            User sender = mock(User.class);
            DmRoom room = mock(DmRoom.class);
            when(userRepository.findById(3L)).thenReturn(Optional.of(sender));
            when(room.isParticipant(3L)).thenReturn(false);
            when(dmRoomRepository.findById(1L)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> dmService.sendMessage(3L, 1L, null, "테스트", null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("roomId 지정 - 성공 시 메시지 저장 + DmRoomUpdatedEvent(양쪽 참가자) publish")
        void sendMessage_existingRoom_publishesRoomUpdated() {
            User sender = mock(User.class);
            User user1 = mock(User.class);
            User user2 = mock(User.class);
            when(sender.getId()).thenReturn(1L);
            when(user1.getId()).thenReturn(1L);
            when(user2.getId()).thenReturn(2L);

            DmRoom room = mock(DmRoom.class);
            when(room.getId()).thenReturn(10L);
            when(room.getUser1()).thenReturn(user1);
            when(room.getUser2()).thenReturn(user2);
            when(room.isParticipant(1L)).thenReturn(true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
            when(dmRoomRepository.findById(10L)).thenReturn(Optional.of(room));

            dmService.sendMessage(1L, 10L, null, "안녕", null);

            verify(dmMessageRepository).save(any(DmMessage.class));
            ArgumentCaptor<DmRoomUpdatedEvent> captor = ArgumentCaptor.forClass(DmRoomUpdatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            DmRoomUpdatedEvent event = captor.getValue();
            assertThat(event.roomId()).isEqualTo(10L);
            assertThat(event.targetUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("roomId null + targetNickname - resolver가 방을 resolve하면 그 roomId로 메시지 저장 + 이벤트 publish")
        void sendMessage_draft_createsRoom() {
            User sender = mock(User.class);
            User target = mock(User.class);
            when(sender.getId()).thenReturn(1L);
            when(target.getId()).thenReturn(2L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
            when(userRepository.findByNickname("냥집사")).thenReturn(Optional.of(target));

            when(dmRoomResolver.resolveOrCreateRoomId(sender, target)).thenReturn(7L);

            DmRoom resolvedRoom = mock(DmRoom.class);
            when(resolvedRoom.getId()).thenReturn(7L);
            when(resolvedRoom.getUser1()).thenReturn(sender);
            when(resolvedRoom.getUser2()).thenReturn(target);
            when(dmRoomRepository.findById(7L)).thenReturn(Optional.of(resolvedRoom));

            dmService.sendMessage(1L, null, "냥집사", "첫 메시지", null);

            verify(dmRoomResolver).resolveOrCreateRoomId(sender, target);
            verify(dmMessageRepository).save(any(DmMessage.class));
            ArgumentCaptor<DmRoomUpdatedEvent> captor = ArgumentCaptor.forClass(DmRoomUpdatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().roomId()).isEqualTo(7L);
            assertThat(captor.getValue().targetUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("미팔로우 상대에게도 draft 전송 가능 - resolver로 방 resolve·메시지 저장")
        void sendMessage_draft_notFollowing_createsRoom() {
            User sender = mock(User.class);
            User target = mock(User.class);
            when(sender.getId()).thenReturn(1L);
            when(target.getId()).thenReturn(2L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
            when(userRepository.findByNickname("낯선유저")).thenReturn(Optional.of(target));

            when(dmRoomResolver.resolveOrCreateRoomId(sender, target)).thenReturn(9L);

            DmRoom resolvedRoom = mock(DmRoom.class);
            when(resolvedRoom.getId()).thenReturn(9L);
            when(resolvedRoom.getUser1()).thenReturn(sender);
            when(resolvedRoom.getUser2()).thenReturn(target);
            when(dmRoomRepository.findById(9L)).thenReturn(Optional.of(resolvedRoom));

            dmService.sendMessage(1L, null, "낯선유저", "안녕 처음 봐요", null);

            verify(dmRoomResolver).resolveOrCreateRoomId(sender, target);
            verify(dmMessageRepository).save(any(DmMessage.class));
        }

        @Test
        @DisplayName("roomId null + targetNickname null - BAD_REQUEST")
        void sendMessage_draft_noTarget_throws() {
            User sender = mock(User.class);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sender));

            assertThatThrownBy(() -> dmService.sendMessage(1L, null, null, "내용", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.BAD_REQUEST));
            verify(dmMessageRepository, never()).save(any(DmMessage.class));
        }
    }

    @Nested
    @DisplayName("markAsRead 메서드")
    class MarkAsReadTest {

        @Test
        @DisplayName("참여자가 아니면 FORBIDDEN")
        void markAsRead_notParticipant() {
            DmRoom room = mock(DmRoom.class);
            when(room.isParticipant(3L)).thenReturn(false);
            when(dmRoomRepository.findById(1L)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> dmService.markAsRead(3L, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("markAllAsRead > 0 시 DmMessagesReadEvent + DmRoomUpdatedEvent(본인) 둘 다 publish")
        void markAsRead_marked_publishesBothEvents() {
            DmRoom room = mock(DmRoom.class);
            User user = mock(User.class);
            when(room.getId()).thenReturn(10L);
            when(room.isParticipant(1L)).thenReturn(true);
            when(dmRoomRepository.findById(10L)).thenReturn(Optional.of(room));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(user.getId()).thenReturn(1L);
            when(user.getNickname()).thenReturn("멍집사");
            when(dmMessageRepository.markAllAsRead(room, user)).thenReturn(3);

            dmService.markAsRead(1L, 10L);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, times(2)).publishEvent(captor.capture());

            DmMessagesReadEvent readEvent = captor.getAllValues().stream()
                    .filter(DmMessagesReadEvent.class::isInstance)
                    .map(DmMessagesReadEvent.class::cast)
                    .findFirst().orElseThrow();
            assertThat(readEvent.roomId()).isEqualTo(10L);
            assertThat(readEvent.readerNickname()).isEqualTo("멍집사");

            DmRoomUpdatedEvent updatedEvent = captor.getAllValues().stream()
                    .filter(DmRoomUpdatedEvent.class::isInstance)
                    .map(DmRoomUpdatedEvent.class::cast)
                    .findFirst().orElseThrow();
            assertThat(updatedEvent.roomId()).isEqualTo(10L);
            assertThat(updatedEvent.targetUserIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("markAllAsRead == 0 시 어떤 이벤트도 미publish")
        void markAsRead_nothing_noEvent() {
            DmRoom room = mock(DmRoom.class);
            User user = mock(User.class);
            when(room.isParticipant(1L)).thenReturn(true);
            when(dmRoomRepository.findById(10L)).thenReturn(Optional.of(room));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(dmMessageRepository.markAllAsRead(room, user)).thenReturn(0);

            dmService.markAsRead(1L, 10L);

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("getMessages 메서드")
    class GetMessagesTest {

        @Test
        @DisplayName("참여자가 아니면 FORBIDDEN")
        void getMessages_notParticipant() {
            DmRoom room = mock(DmRoom.class);
            when(room.isParticipant(3L)).thenReturn(false);
            when(dmRoomRepository.findById(1L)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> dmService.getMessages(1L, 3L, 0, 50))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("markAllAsRead > 0 시 읽음 이벤트 2종 publish")
        void getMessages_markedRead_publishesEvents() {
            DmRoom room = mock(DmRoom.class);
            User user = mock(User.class);
            when(room.getId()).thenReturn(10L);
            when(room.isParticipant(1L)).thenReturn(true);
            when(dmRoomRepository.findById(10L)).thenReturn(Optional.of(room));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(user.getId()).thenReturn(1L);
            when(user.getNickname()).thenReturn("멍집사");
            when(dmMessageRepository.markAllAsRead(room, user)).thenReturn(3);
            when(dmMessageRepository.findAllByRoomOrderByCreatedAtDesc(eq(room), any()))
                    .thenReturn(Page.empty());

            dmService.getMessages(10L, 1L, 0, 50);

            verify(eventPublisher, times(2)).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("markAllAsRead == 0 시 이벤트 미publish")
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
