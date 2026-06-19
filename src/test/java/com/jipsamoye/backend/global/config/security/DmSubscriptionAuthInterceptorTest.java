package com.jipsamoye.backend.global.config.security;

import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * C-1: DM 방 구독 인가 인터셉터 통합 테스트.
 * 실제 영속화된 DmRoom으로 참여/비참여 SUBSCRIBE를 구동해, 비참여자의 타인 DM 도청이 차단되는지와
 * 공개 채팅·user-destination·비-SUBSCRIBE 프레임은 통과하는지 검증한다.
 */
@DataJpaTest
@ActiveProfiles("datajpatest")
@DisplayName("DmSubscriptionAuthInterceptor - DM 방 구독 인가")
class DmSubscriptionAuthInterceptorTest {

    @Autowired
    private DmRoomRepository dmRoomRepository;

    @Autowired
    private UserRepository userRepository;

    private final MessageChannel channel = mock(MessageChannel.class);

    private DmSubscriptionAuthInterceptor interceptor;

    private User participant;
    private User other;
    private User outsider;
    private DmRoom room;

    @BeforeEach
    void setUp() {
        interceptor = new DmSubscriptionAuthInterceptor(dmRoomRepository);

        participant = userRepository.save(newUser("참여자", "p@test.com", "pid-p"));
        other = userRepository.save(newUser("상대", "o@test.com", "pid-o"));
        outsider = userRepository.save(newUser("외부인", "x@test.com", "pid-x"));

        room = dmRoomRepository.save(DmRoom.builder().user1(participant).user2(other).build());
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

    private Message<byte[]> subscribe(String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (userId != null) {
            Map<String, Object> sessionAttributes = new HashMap<>();
            sessionAttributes.put("userId", userId);
            accessor.setSessionAttributes(sessionAttributes);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand command, String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (userId != null) {
            sessionAttributes.put("userId", userId);
        }
        accessor.setSessionAttributes(sessionAttributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Nested
    @DisplayName("DM 방 토픽 SUBSCRIBE 인가")
    class DmRoomSubscribe {

        @Test
        @DisplayName("참여자의 구독 - 통과(메시지 그대로 반환)")
        void participant_allowed() {
            Message<byte[]> msg = subscribe("/sub/dm/room/" + room.getId(), participant.getId());

            Message<?> result = interceptor.preSend(msg, channel);

            assertThat(result).isSameAs(msg);
        }

        @Test
        @DisplayName("상대 참여자의 구독 - 통과")
        void otherParticipant_allowed() {
            Message<byte[]> msg = subscribe("/sub/dm/room/" + room.getId(), other.getId());

            assertThatCode(() -> interceptor.preSend(msg, channel)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("비참여자(외부인)의 구독 - 차단(MessageDeliveryException) → 타인 DM 도청 방지")
        void outsider_blocked() {
            Message<byte[]> msg = subscribe("/sub/dm/room/" + room.getId(), outsider.getId());

            assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                    .isInstanceOf(MessageDeliveryException.class);
        }

        @Test
        @DisplayName("존재하지 않는 roomId 구독 - 차단")
        void nonExistentRoom_blocked() {
            Message<byte[]> msg = subscribe("/sub/dm/room/999999", participant.getId());

            assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                    .isInstanceOf(MessageDeliveryException.class);
        }

        @Test
        @DisplayName("미인증(세션 userId 없음) 구독 - 차단")
        void unauthenticated_blocked() {
            Message<byte[]> msg = subscribe("/sub/dm/room/" + room.getId(), null);

            assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                    .isInstanceOf(MessageDeliveryException.class);
        }

        @Test
        @DisplayName("DM 방 네임스페이스이나 roomId가 숫자가 아님(/sub/dm/room/abc) - 차단(deny-by-default)")
        void nonNumericRoomId_blocked() {
            // 이전엔 정규식 비매칭으로 통과시켰으나, deny-by-default 정책상 거부가 맞다.
            Message<byte[]> msg = subscribe("/sub/dm/room/abc", outsider.getId());

            assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                    .isInstanceOf(MessageDeliveryException.class);
        }

        @Test
        @DisplayName("destination 없는 SUBSCRIBE - 차단(deny-by-default)")
        void nullDestination_blocked() {
            Message<byte[]> msg = subscribe(null, participant.getId());

            assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    /**
     * BLOCKER 회귀 방지: SimpleBroker의 DefaultSubscriptionRegistry는 구독 destination을
     * AntPathMatcher 패턴으로 매칭하므로, 와일드카드 구독을 허용하면 인증된 사용자 1명이
     * 단일 구독(예: /sub/dm/room/*)으로 임의 방 메시지를 도청할 수 있다.
     * 인터셉터가 preSend에서 패턴 구독을 거부하면 브로커 레지스트리에 등록 자체가 안 되므로,
     * 아래 인터셉터 레벨 거부 단언이 도청 차단의 충분조건이다.
     * userId는 참여자(인가된 세션)로 두어 "인가된 사용자라도 와일드카드 구독은 거부"됨을 보장한다.
     */
    @Nested
    @DisplayName("와일드카드 패턴 구독 우회 차단(C-1 BLOCKER 회귀 방지)")
    class WildcardBypassBlocked {

        @ParameterizedTest(name = "[{index}] {0} - 차단")
        @ValueSource(strings = {
                "/sub/dm/room/*",
                "/sub/dm/room/**",
                "/sub/dm/**",
                "/sub/**",
                "/sub/dm/room/12?",
                "/sub/dm/room/{id}"
        })
        @DisplayName("와일드카드 destination 구독 - 차단(인가된 세션이어도)")
        void wildcardDestination_blocked(String destination) {
            Message<byte[]> msg = subscribe(destination, participant.getId());

            assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    @Nested
    @DisplayName("인가 대상이 아닌 destination·프레임은 통과")
    class PassThrough {

        @Test
        @DisplayName("공개 오픈채팅 /sub/chat/room 구독 - 통과(비참여 외부인도 허용)")
        void publicChat_passesThrough() {
            Message<byte[]> msg = subscribe("/sub/chat/room", outsider.getId());

            assertThatCode(() -> interceptor.preSend(msg, channel)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("user-destination /user/sub/dm/rooms 구독 - 통과(미차단)")
        void userDestinationRooms_passesThrough() {
            Message<byte[]> msg = subscribe("/user/sub/dm/rooms", outsider.getId());

            assertThatCode(() -> interceptor.preSend(msg, channel)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("user-destination /user/sub/notifications 구독 - 통과(미차단)")
        void userDestinationNotifications_passesThrough() {
            Message<byte[]> msg = subscribe("/user/sub/notifications", outsider.getId());

            assertThatCode(() -> interceptor.preSend(msg, channel)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DM 방 목록 토픽 /sub/dm/rooms 구독 - 통과(/sub/dm/room/ prefix 아님: 슬래시 위치 차이)")
        void dmRoomsList_passesThrough() {
            // /sub/dm/rooms 는 /sub/dm/room/ prefix로 시작하지 않으므로 roomId 검증 분기에 진입하지 않는다.
            Message<byte[]> msg = subscribe("/sub/dm/rooms", outsider.getId());

            assertThatCode(() -> interceptor.preSend(msg, channel)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SEND 프레임(비-SUBSCRIBE)은 DM 방 destination이어도 인터셉터가 간섭하지 않음")
        void sendFrame_notIntercepted() {
            Message<byte[]> msg = frame(StompCommand.SEND, "/pub/dm/send", outsider.getId());

            assertThatCode(() -> interceptor.preSend(msg, channel)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CONNECT 프레임은 통과")
        void connectFrame_notIntercepted() {
            Message<byte[]> msg = frame(StompCommand.CONNECT, null, outsider.getId());

            assertThatCode(() -> interceptor.preSend(msg, channel)).doesNotThrowAnyException();
        }
    }
}
