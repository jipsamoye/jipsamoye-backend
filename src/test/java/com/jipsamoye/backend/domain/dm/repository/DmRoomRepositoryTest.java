package com.jipsamoye.backend.domain.dm.repository;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomProjection;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("datajpatest")
@DisplayName("DmRoomRepository.findAllByUser - 빈 방 제외 검증")
class DmRoomRepositoryTest {

    @Autowired
    private DmRoomRepository dmRoomRepository;

    @Autowired
    private DmMessageRepository dmMessageRepository;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = userRepository.save(newUser("user1", "u1@test.com", "pid1"));
        user2 = userRepository.save(newUser("user2", "u2@test.com", "pid2"));
    }

    private User newUser(String nickname, String email, String providerId) {
        return User.builder()
                .nickname(nickname)
                .email(email)
                .provider(Provider.GOOGLE)
                .providerId(providerId)
                .role(Role.USER)
                .build();
    }

    private DmRoom newRoom(User a, User b) {
        return dmRoomRepository.save(DmRoom.builder().user1(a).user2(b).build());
    }

    private void sendMessage(DmRoom room, User sender, String content) {
        dmMessageRepository.save(DmMessage.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .build());
    }

    @Nested
    @DisplayName("메시지가 없는 빈 방")
    class EmptyRoom {

        @Test
        @DisplayName("방만 만들고 메시지 미전송 시 - user1(생성자) 관점에서 목록에 없음")
        void emptyRoom_excludedForUser1() {
            newRoom(user1, user2);

            List<DmRoom> rooms = dmRoomRepository.findAllByUser(user1);

            assertThat(rooms).isEmpty();
        }

        @Test
        @DisplayName("방만 만들고 메시지 미전송 시 - user2(상대) 관점에서 목록에 없음")
        void emptyRoom_excludedForUser2() {
            newRoom(user1, user2);

            List<DmRoom> rooms = dmRoomRepository.findAllByUser(user2);

            assertThat(rooms).isEmpty();
        }
    }

    @Nested
    @DisplayName("메시지가 1개 이상 오간 방")
    class RoomWithMessage {

        @Test
        @DisplayName("메시지가 있으면 - user1(생성자) 관점에서 목록에 있음")
        void roomWithMessage_includedForUser1() {
            DmRoom room = newRoom(user1, user2);
            sendMessage(room, user1, "안녕하세요");

            List<DmRoom> rooms = dmRoomRepository.findAllByUser(user1);

            assertThat(rooms).hasSize(1);
            assertThat(rooms.get(0).getId()).isEqualTo(room.getId());
        }

        @Test
        @DisplayName("메시지가 있으면 - user2(상대) 관점에서 목록에 있음")
        void roomWithMessage_includedForUser2() {
            DmRoom room = newRoom(user1, user2);
            sendMessage(room, user1, "안녕하세요");

            List<DmRoom> rooms = dmRoomRepository.findAllByUser(user2);

            assertThat(rooms).hasSize(1);
            assertThat(rooms.get(0).getId()).isEqualTo(room.getId());
        }
    }

    @Nested
    @DisplayName("existsByIdAndParticipant - 구독 인가용 참여자 확인")
    class ExistsByIdAndParticipant {

        @Test
        @DisplayName("user1로 저장된 참여자 - true")
        void user1_isParticipant() {
            DmRoom room = newRoom(user1, user2);

            assertThat(dmRoomRepository.existsByIdAndParticipant(room.getId(), user1.getId())).isTrue();
        }

        @Test
        @DisplayName("user2로 저장된 참여자 - true (양방향)")
        void user2_isParticipant() {
            DmRoom room = newRoom(user1, user2);

            assertThat(dmRoomRepository.existsByIdAndParticipant(room.getId(), user2.getId())).isTrue();
        }

        @Test
        @DisplayName("비참여자 - false")
        void outsider_isNotParticipant() {
            User user3 = userRepository.save(newUser("user3", "u3@test.com", "pid3"));
            DmRoom room = newRoom(user1, user2);

            assertThat(dmRoomRepository.existsByIdAndParticipant(room.getId(), user3.getId())).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 roomId - false")
        void nonExistentRoom_false() {
            assertThat(dmRoomRepository.existsByIdAndParticipant(999999L, user1.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("빈 방과 메시지 있는 방 혼재")
    class MixedRooms {

        @Test
        @DisplayName("빈 방은 제외되고 메시지 있는 방만 반환 - 양쪽 참가자 모두 동일")
        void onlyRoomsWithMessagesReturned() {
            User user3 = userRepository.save(newUser("user3", "u3@test.com", "pid3"));

            DmRoom emptyRoom = newRoom(user1, user2);
            DmRoom activeRoom = newRoom(user1, user3);
            sendMessage(activeRoom, user3, "반가워요");

            List<DmRoom> roomsForUser1 = dmRoomRepository.findAllByUser(user1);
            assertThat(roomsForUser1).extracting(DmRoom::getId)
                    .containsExactly(activeRoom.getId())
                    .doesNotContain(emptyRoom.getId());

            List<DmRoom> roomsForUser3 = dmRoomRepository.findAllByUser(user3);
            assertThat(roomsForUser3).extracting(DmRoom::getId)
                    .containsExactly(activeRoom.getId());
        }
    }

    @Nested
    @DisplayName("findRoomSummaries - 방 목록 단일 쿼리 프로젝션(N+1 제거)")
    class FindRoomSummaries {

        @Test
        @DisplayName("마지막 메시지(MAX id 기준)·상대방·unread를 한 번에 매핑 (user1 관점)")
        void summaries_lastMessageAndUnread_forUser1() {
            DmRoom room = newRoom(user1, user2);
            // user2가 보낸 미읽음 메시지 2건 + user1이 보낸 메시지 1건(가장 최근)
            sendMessage(room, user2, "안녕1");
            sendMessage(room, user2, "안녕2");
            sendMessage(room, user1, "마지막");

            List<DmRoomProjection> summaries = dmRoomRepository.findRoomSummaries(user1.getId());

            assertThat(summaries).hasSize(1);
            DmRoomProjection s = summaries.get(0);
            assertThat(s.roomId()).isEqualTo(room.getId());
            // user1 관점에서 상대방은 user2
            assertThat(s.otherUserNickname()).isEqualTo("user2");
            // 마지막 메시지는 MAX(id) = "마지막"
            assertThat(s.lastMessage()).isEqualTo("마지막");
            assertThat(s.lastMessageAt()).isNotNull();
            // user1 입장 unread = user2가 보낸 미읽음 2건 (본인이 보낸 건 unread 아님)
            assertThat(s.unreadCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("상대방 식별이 user2 관점에서도 정확 (CASE 양방향)")
        void summaries_otherUser_forUser2() {
            DmRoom room = newRoom(user1, user2);
            sendMessage(room, user1, "user1이 보냄");

            List<DmRoomProjection> summaries = dmRoomRepository.findRoomSummaries(user2.getId());

            assertThat(summaries).hasSize(1);
            DmRoomProjection s = summaries.get(0);
            // user2 관점에서 상대방은 user1
            assertThat(s.otherUserNickname()).isEqualTo("user1");
            // user2 입장 unread = user1이 보낸 미읽음 1건
            assertThat(s.unreadCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("읽음 처리된 메시지는 unread에서 제외")
        void summaries_readMessagesExcludedFromUnread() {
            DmRoom room = newRoom(user1, user2);
            sendMessage(room, user2, "읽을 메시지");
            sendMessage(room, user2, "안 읽은 메시지");

            // user1이 읽음 처리 → user2가 보낸 미읽음 메시지가 모두 읽힘
            dmMessageRepository.markAllAsRead(room, user1);

            List<DmRoomProjection> summaries = dmRoomRepository.findRoomSummaries(user1.getId());

            assertThat(summaries).hasSize(1);
            assertThat(summaries.get(0).unreadCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("빈 방은 EXISTS(message) 필터로 제외")
        void summaries_emptyRoomExcluded() {
            newRoom(user1, user2);

            assertThat(dmRoomRepository.findRoomSummaries(user1.getId())).isEmpty();
        }

        @Test
        @DisplayName("여러 방을 findAllByUser와 동일한 updatedAt DESC 순서로 반환")
        void summaries_orderConsistentWithFindAllByUser() {
            User user3 = userRepository.save(newUser("user3", "u3@test.com", "pid3"));

            DmRoom roomA = newRoom(user1, user2);
            sendMessage(roomA, user2, "A");
            DmRoom roomB = newRoom(user1, user3);
            sendMessage(roomB, user3, "B");

            // 절대 타임스탬프 제어는 auditing 의존이라 불안정하므로, 동일 데이터에서
            // 기존 findAllByUser(검증된 ORDER BY updatedAt DESC)와 순서가 일치하는지 비교한다.
            List<Long> expectedOrder = dmRoomRepository.findAllByUser(user1).stream()
                    .map(DmRoom::getId)
                    .toList();
            List<Long> actualOrder = dmRoomRepository.findRoomSummaries(user1.getId()).stream()
                    .map(DmRoomProjection::roomId)
                    .toList();

            assertThat(actualOrder)
                    .containsExactlyElementsOf(expectedOrder)
                    .containsExactlyInAnyOrder(roomA.getId(), roomB.getId());
        }
    }
}
