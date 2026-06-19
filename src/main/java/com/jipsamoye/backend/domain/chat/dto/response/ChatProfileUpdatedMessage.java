package com.jipsamoye.backend.domain.chat.dto.response;

import com.jipsamoye.backend.domain.user.event.ProfileUpdatedEvent;

/**
 * 오픈채팅 토픽({@code /sub/chat/room})으로 보내는 프로필 변경 알림 메시지.
 *
 * <p>기존에는 {@code Map.of("type", ..., "nickname", ..., "profileImageUrl", ...)}로 타입 없는
 * payload를 보냈다. 타입 있는 record로 바꿔 직렬화 형식을 계약으로 고정한다. JSON 키
 * ({@code type}/{@code nickname}/{@code profileImageUrl})는 그대로 유지한다.
 *
 * <p>profileImageUrl이 null이면 빈 문자열로 정규화해(프론트가 null/누락을 구분하지 않도록) 전송한다.
 */
public record ChatProfileUpdatedMessage(
        String type,
        String nickname,
        String profileImageUrl
) {
    private static final String TYPE = "PROFILE_UPDATED";

    public static ChatProfileUpdatedMessage from(ProfileUpdatedEvent event) {
        String imageUrl = event.getProfileImageUrl() != null ? event.getProfileImageUrl() : "";
        return new ChatProfileUpdatedMessage(TYPE, event.getNickname(), imageUrl);
    }
}
