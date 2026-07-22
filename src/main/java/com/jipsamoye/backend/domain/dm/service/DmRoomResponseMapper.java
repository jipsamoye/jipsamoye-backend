package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomProjection;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * {@link DmRoomResponse} 생성 로직을 한 곳에 모은 매퍼.
 *
 * <p>{@code getRooms}(프로젝션 기반)와 {@code DmRoomUpdatedEventListener}(엔티티 기반)가 같은 응답
 * 형식을 만들면서 매핑 코드를 중복 작성하던 것을 통합한다. 응답 필드 계약이 바뀌면 이 매퍼만 고치면 된다.
 */
public final class DmRoomResponseMapper {

    private DmRoomResponseMapper() {
    }

    /** 단일 쿼리 프로젝션 → 응답. ({@code getRooms} 경로) */
    public static DmRoomResponse from(DmRoomProjection projection) {
        return new DmRoomResponse(
                projection.roomId(),
                projection.otherUserNickname(),
                projection.otherUserProfileImageUrl(),
                projection.lastMessage(),
                projection.lastMessageAt(),
                projection.unreadCount()
        );
    }

    /**
     * 엔티티 조각 → 응답. ({@code DmRoomUpdatedEventListener} 경로)
     * 상대방 식별과 마지막 메시지 추출은 호출자가 수행하고, 응답 조립만 여기서 담당한다.
     */
    public static DmRoomResponse of(Long roomId, User other, DmMessage lastMessage, long unreadCount) {
        String lastContent = lastMessage != null ? lastMessage.getContent() : null;
        LocalDateTime lastAt = lastMessage != null ? lastMessage.getCreatedAt() : null;
        return new DmRoomResponse(
                roomId,
                other.getNickname(),
                other.getProfileImageUrl(),
                lastContent,
                lastAt,
                unreadCount
        );
    }
}
