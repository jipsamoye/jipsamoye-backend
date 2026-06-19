package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmRoomProjection;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DmRoomResponseMapper 단위 테스트")
class DmRoomResponseMapperTest {

    @Test
    @DisplayName("프로젝션 → 응답 (필드 그대로 매핑)")
    void from_projection() {
        LocalDateTime at = LocalDateTime.of(2026, 6, 19, 12, 0);
        DmRoomProjection projection = new DmRoomProjection(5L, "상대", "img", "마지막", at, 4L);

        DmRoomResponse response = DmRoomResponseMapper.from(projection);

        assertThat(response.roomId()).isEqualTo(5L);
        assertThat(response.otherUserNickname()).isEqualTo("상대");
        assertThat(response.otherUserProfileImageUrl()).isEqualTo("img");
        assertThat(response.lastMessage()).isEqualTo("마지막");
        assertThat(response.lastMessageAt()).isEqualTo(at);
        assertThat(response.unreadCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("엔티티 조각 → 응답 (마지막 메시지 있음)")
    void of_withLastMessage() {
        User other = mock(User.class);
        when(other.getNickname()).thenReturn("상대");
        when(other.getProfileImageUrl()).thenReturn("img");
        DmMessage lastMessage = mock(DmMessage.class);
        LocalDateTime at = LocalDateTime.of(2026, 6, 19, 12, 0);
        when(lastMessage.getContent()).thenReturn("내용");
        when(lastMessage.getCreatedAt()).thenReturn(at);

        DmRoomResponse response = DmRoomResponseMapper.of(7L, other, lastMessage, 2L);

        assertThat(response.roomId()).isEqualTo(7L);
        assertThat(response.otherUserNickname()).isEqualTo("상대");
        assertThat(response.lastMessage()).isEqualTo("내용");
        assertThat(response.lastMessageAt()).isEqualTo(at);
        assertThat(response.unreadCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("엔티티 조각 → 응답 (마지막 메시지 null → lastMessage/lastMessageAt null)")
    void of_nullLastMessage() {
        User other = mock(User.class);
        when(other.getNickname()).thenReturn("상대");
        when(other.getProfileImageUrl()).thenReturn(null);

        DmRoomResponse response = DmRoomResponseMapper.of(null, other, null, 0L);

        assertThat(response.roomId()).isNull();
        assertThat(response.otherUserNickname()).isEqualTo("상대");
        assertThat(response.otherUserProfileImageUrl()).isNull();
        assertThat(response.lastMessage()).isNull();
        assertThat(response.lastMessageAt()).isNull();
        assertThat(response.unreadCount()).isEqualTo(0L);
    }
}
