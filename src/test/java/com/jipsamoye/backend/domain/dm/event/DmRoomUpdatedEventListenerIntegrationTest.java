package com.jipsamoye.backend.domain.dm.event;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * DmRoomUpdatedEventListener 통합 테스트.
 * 단위 테스트(mock 기반)와 달리 실제 영속화된 엔티티(방 + 양쪽 user + 메시지)를 사용해
 * 실제 lazy 로딩과 unread 재조회를 거친 뒤 수신자별 DmRoomResponse가 올바르게 전송되는지 검증한다.
 * SimpMessagingTemplate은 mock으로 두고 호출 인자를 검증한다.
 */
@DataJpaTest
@ActiveProfiles("datajpatest")
@DisplayName("DmRoomUpdatedEventListener 통합 테스트")
class DmRoomUpdatedEventListenerIntegrationTest {

    @Autowired
    private DmRoomRepository dmRoomRepository;

    @Autowired
    private DmMessageRepository dmMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

    private DmRoomUpdatedEventListener listener;

    private User sender;
    private User receiver;
    private DmRoom room;

    @BeforeEach
    void setUp() {
        listener = new DmRoomUpdatedEventListener(
                dmRoomRepository, dmMessageRepository, userRepository, messagingTemplate);

        sender = userRepository.save(newUser("보낸이", "sender@test.com", "pid-s", "sender-img"));
        receiver = userRepository.save(newUser("받는이", "receiver@test.com", "pid-r", "receiver-img"));

        room = dmRoomRepository.save(DmRoom.builder().user1(sender).user2(receiver).build());
        dmMessageRepository.save(DmMessage.builder()
                .room(room)
                .sender(sender)
                .content("첫 메시지")
                .build());

        // 영속성 컨텍스트를 비워 listener가 실제 DB 재조회와 lazy 로딩을 수행하도록 한다.
        entityManager.flush();
        entityManager.clear();
    }

    private User newUser(String nickname, String email, String providerId, String profileImageUrl) {
        return User.builder()
                .nickname(nickname)
                .email(email)
                .profileImageUrl(profileImageUrl)
                .provider(Provider.GOOGLE)
                .providerId(providerId)
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("실제 엔티티로 수신자별 상대방/unread를 계산해 '/sub/dm/rooms'로 전송")
    void handle_sendsPerRecipientRoomResponse_withRealEntities() {
        listener.handle(new DmRoomUpdatedEvent(room.getId(), List.of(sender.getId(), receiver.getId())));

        // sender 관점: 상대방 = receiver, unread = 0 (본인이 보낸 메시지는 unread 아님)
        ArgumentCaptor<DmRoomResponse> senderCaptor = ArgumentCaptor.forClass(DmRoomResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(String.valueOf(sender.getId())), eq("/sub/dm/rooms"), senderCaptor.capture());
        DmRoomResponse senderPayload = senderCaptor.getValue();
        assertThat(senderPayload.roomId()).isEqualTo(room.getId());
        assertThat(senderPayload.otherUserNickname()).isEqualTo("받는이");
        assertThat(senderPayload.otherUserProfileImageUrl()).isEqualTo("receiver-img");
        assertThat(senderPayload.lastMessage()).isEqualTo("첫 메시지");
        assertThat(senderPayload.unreadCount()).isEqualTo(0L);

        // receiver 관점: 상대방 = sender, unread = 1 (sender가 보낸 미읽음 메시지 1건)
        ArgumentCaptor<DmRoomResponse> receiverCaptor = ArgumentCaptor.forClass(DmRoomResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(String.valueOf(receiver.getId())), eq("/sub/dm/rooms"), receiverCaptor.capture());
        DmRoomResponse receiverPayload = receiverCaptor.getValue();
        assertThat(receiverPayload.roomId()).isEqualTo(room.getId());
        assertThat(receiverPayload.otherUserNickname()).isEqualTo("보낸이");
        assertThat(receiverPayload.otherUserProfileImageUrl()).isEqualTo("sender-img");
        assertThat(receiverPayload.lastMessage()).isEqualTo("첫 메시지");
        assertThat(receiverPayload.unreadCount()).isEqualTo(1L);
    }
}
