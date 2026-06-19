package com.jipsamoye.backend.domain.dm.dto.response;

import java.time.LocalDateTime;

/**
 * {@code getRooms} N+1 제거용 프로젝션. 방 목록을 단일 쿼리로 가져온다.
 *
 * <p>기존 구현은 방마다 마지막 메시지 조회 + unread count + LAZY 상대방 프록시 접근으로 최소 2N+α
 * 쿼리를 냈다. 이 프로젝션은 상관 서브쿼리(마지막 메시지·unread)와 CASE(상대방 식별)를 한 쿼리에 담아
 * 방 개수와 무관하게 상수 쿼리로 처리한다.
 *
 * <p><b>마지막 메시지 식별</b>은 {@code MAX(id)}를 쓴다. id가 IDENTITY(단조 증가)라 동일 createdAt
 * 타이 상황에서도 가장 최근 메시지가 모호하지 않게 결정된다.
 *
 * <p>{@link DmRoomResponse}와 동일한 필드 의미를 갖되, 매핑은 {@code DmRoomResponseMapper}가 담당한다.
 */
public record DmRoomProjection(
        Long roomId,
        String otherUserNickname,
        String otherUserProfileImageUrl,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount
) {
}
