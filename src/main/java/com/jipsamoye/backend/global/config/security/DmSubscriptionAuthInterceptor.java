package com.jipsamoye.backend.global.config.security;

import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * DM 방 토픽({@code /sub/dm/room/{roomId}}) 구독(SUBSCRIBE) 인가 인터셉터.
 *
 * <p>SimpleBroker는 STOMP SUBSCRIBE를 인가하지 않으므로, inbound 채널에서 SUBSCRIBE 프레임을
 * 가로채 비참여자가 타인의 DM 방을 구독(도청)하는 것을 차단한다. 전송/조회/읽음의
 * 서비스 계층 isParticipant 검증과 동일한 인가 기준을 구독에도 적용한다.
 *
 * <p><b>deny-by-default(거부 우선)</b> 정책으로 동작한다. SimpleBroker의
 * {@code DefaultSubscriptionRegistry}는 구독 destination을 {@link org.springframework.util.AntPathMatcher}
 * <b>패턴</b>으로 매칭하므로, 와일드카드 구독(예: {@code /sub/dm/room/*}, {@code /sub/**})을 허용하면
 * 공격자가 단일 구독으로 임의의 {@code /sub/dm/room/{id}} 메시지를 수신(도청)할 수 있다.
 * 따라서 Ant/STOMP 와일드카드 문자({@code *}, {@code ?}, {@code "{"})가 포함된 destination은
 * 무조건 거부한다. 정상 클라이언트는 구체적인 destination만 구독한다.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>SUBSCRIBE 외 STOMP command → 통과
 *   <li>destination == null → 거부(정상 SUBSCRIBE가 아님)
 *   <li>와일드카드 문자({@code *}, {@code ?}, {@code "{"}) 포함 → 거부(패턴 구독 도청 차단)
 *   <li>{@code /sub/dm/room/}로 시작 → 나머지가 숫자(roomId)가 아니면 거부, 숫자면 참여 검증
 *   <li>그 외 구체 destination → 통과
 * </ol>
 *
 * <p>통과시키는 정상 destination 예:
 * <ul>
 *   <li>{@code /sub/chat/room} — 공개 오픈채팅
 *   <li>{@code /sub/dm/rooms} — DM 방 목록 토픽({@code /sub/dm/room/} prefix로 시작하지 않음: 슬래시 위치 차이)
 *   <li>{@code /user/**} — user-destination(Principal 기반 사용자별 격리. {@code /user/sub/dm/rooms},
 *       {@code /user/sub/notifications} 등. raw destination이 {@code /user/...})
 *   <li>{@code /sub/dm/room/{숫자}} — 참여자 본인의 DM 방
 *   <li>SUBSCRIBE 외 모든 STOMP command
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmSubscriptionAuthInterceptor implements ChannelInterceptor {

    /** DM 방 토픽 네임스페이스 prefix. 이 prefix로 시작하면 roomId 검증 대상이다. */
    private static final String DM_ROOM_PREFIX = "/sub/dm/room/";

    private final DmRoomRepository dmRoomRepository;

    @Override
    @Transactional(readOnly = true)
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();

        // (1) destination 누락: 정상 SUBSCRIBE가 아니므로 거부.
        if (destination == null) {
            log.warn("[WS-AUTH] destination 없는 SUBSCRIBE 차단: userId={}", resolveUserId(accessor));
            throw new MessageDeliveryException("Forbidden subscription: missing destination");
        }

        // (2) 와일드카드 패턴 구독 거부.
        //     SimpleBroker가 AntPathMatcher 패턴으로 배달하므로 패턴 구독을 허용하면 도청이 가능하다.
        //     AntPathMatcher.isPattern() 대상 전부(*, ?, {)를 char 포함 검사로 일괄 차단한다.
        if (isPattern(destination)) {
            log.warn("[WS-AUTH] 와일드카드 패턴 구독 차단: userId={}, destination={}",
                    resolveUserId(accessor), destination);
            throw new MessageDeliveryException("Forbidden subscription: wildcard destination not allowed");
        }

        // (3) DM 방 네임스페이스: deny-by-default. 숫자 roomId만 허용하고 참여 검증한다.
        if (destination.startsWith(DM_ROOM_PREFIX)) {
            return authorizeDmRoomSubscription(destination, accessor, message);
        }

        // (4) 그 외 구체 destination(공개 채팅·DM 방 목록·user-destination 등) → 통과.
        return message;
    }

    private Message<?> authorizeDmRoomSubscription(String destination, StompHeaderAccessor accessor,
                                                  Message<?> message) {
        String roomIdPart = destination.substring(DM_ROOM_PREFIX.length());
        Long roomId = parseRoomId(roomIdPart);
        if (roomId == null) {
            log.warn("[WS-AUTH] 비숫자/범위초과 DM 방 구독 시도 차단(deny-by-default): userId={}, destination={}",
                    resolveUserId(accessor), destination);
            throw new MessageDeliveryException("Forbidden DM subscription: invalid room id");
        }

        Long userId = resolveUserId(accessor);

        if (userId == null) {
            log.warn("[WS-AUTH] 미인증 세션의 DM 방 구독 시도 차단: destination={}", destination);
            throw new MessageDeliveryException("Unauthorized DM subscription: not authenticated");
        }

        if (!dmRoomRepository.existsByIdAndParticipant(roomId, userId)) {
            log.warn("[WS-AUTH] 비인가 DM 방 구독 시도 차단: userId={}, roomId={}", userId, roomId);
            throw new MessageDeliveryException("Forbidden DM subscription: not a participant");
        }

        return message;
    }

    /**
     * Ant/STOMP 와일드카드 문자({@code *}, {@code ?}, {@code "{"}) 포함 여부.
     * 하나라도 포함되면 패턴 구독으로 간주해 거부한다.
     */
    private boolean isPattern(String destination) {
        return destination.indexOf('*') != -1
                || destination.indexOf('?') != -1
                || destination.indexOf('{') != -1;
    }

    /**
     * 순수 숫자(부호·공백·기타 문자 불허) roomId를 {@link Long}으로 파싱한다.
     * 비숫자이거나 long 범위를 초과하면 {@code null}을 반환한다(거부 신호).
     */
    private Long parseRoomId(String value) {
        if (value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            return null;
        }
    }

    private Long resolveUserId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        Object userId = sessionAttributes.get("userId");
        return (userId instanceof Long longUserId) ? longUserId : null;
    }
}
