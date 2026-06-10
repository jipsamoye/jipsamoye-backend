package com.jipsamoye.backend.domain.dm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageEvent;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmReadEvent;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Jackson 직렬화 테스트")
class DmJsonSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Nested
    @DisplayName("UserResponse 직렬화")
    class UserResponseSerializationTest {

        @Test
        @DisplayName("UserResponse 직렬화 시 'isFollowing' 키가 존재하고 'following' 키는 없음")
        void userResponse_hasIsFollowingKey_noFollowingKey() throws Exception {
            // Record에서 UserResponse 직렬화 시 isFollowing 필드가 올바른 키로 나오는지 확인
            // User 모킹 없이 직접 생성자 사용 (record는 직접 생성 가능)
            UserResponse response = new UserResponse(
                    "테스트유저", "소개", null, null, null,
                    0L, 0L, 0L, 0L, null, LocalDateTime.now(), true
            );

            String json = objectMapper.writeValueAsString(response);
            JsonNode node = objectMapper.readTree(json);

            assertThat(node.has("isFollowing")).isTrue();
            assertThat(node.has("following")).isFalse();
            assertThat(node.get("isFollowing").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("UserResponse의 createdAt이 ISO-8601 naive 문자열로 직렬화됨")
        void userResponse_createdAt_isIso8601NaiveString() throws Exception {
            LocalDateTime createdAt = LocalDateTime.of(2026, 6, 11, 10, 0, 0);
            UserResponse response = new UserResponse(
                    "테스트유저", "소개", null, null, null,
                    0L, 0L, 0L, 0L, null, createdAt, false
            );

            String json = objectMapper.writeValueAsString(response);
            JsonNode node = objectMapper.readTree(json);

            assertThat(node.get("createdAt").asText()).isEqualTo("2026-06-11T10:00:00");
        }
    }

    @Nested
    @DisplayName("DmMessageEvent 직렬화")
    class DmMessageEventSerializationTest {

        @Test
        @DisplayName("DmMessageEvent JSON 키가 스펙과 일치: type, message.id/senderNickname/content/imageUrl/readAt/createdAt/clientMessageId")
        void dmMessageEvent_keyMatchesSpec() throws Exception {
            LocalDateTime now = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
            DmMessageResponse msgResponse = new DmMessageResponse(
                    1L, "발신자", "내용", "http://image.url", null, now);
            DmMessageEvent event = DmMessageEvent.of(msgResponse, "cid-123");

            String json = objectMapper.writeValueAsString(event);
            JsonNode node = objectMapper.readTree(json);

            assertThat(node.get("type").asText()).isEqualTo("MESSAGE");
            JsonNode message = node.get("message");
            assertThat(message).isNotNull();
            assertThat(message.has("id")).isTrue();
            assertThat(message.get("id").asLong()).isEqualTo(1L);
            assertThat(message.has("senderNickname")).isTrue();
            assertThat(message.get("senderNickname").asText()).isEqualTo("발신자");
            assertThat(message.has("content")).isTrue();
            assertThat(message.has("imageUrl")).isTrue();
            assertThat(message.has("readAt")).isTrue();
            assertThat(message.has("createdAt")).isTrue();
            assertThat(message.has("clientMessageId")).isTrue();
            assertThat(message.get("clientMessageId").asText()).isEqualTo("cid-123");
        }

        @Test
        @DisplayName("DmMessageEvent의 createdAt이 ISO-8601 naive 문자열로 직렬화됨")
        void dmMessageEvent_createdAt_isIso8601NaiveString() throws Exception {
            LocalDateTime createdAt = LocalDateTime.of(2026, 6, 11, 10, 0, 0);
            DmMessageResponse msgResponse = new DmMessageResponse(
                    1L, "발신자", "내용", null, null, createdAt);
            DmMessageEvent event = DmMessageEvent.of(msgResponse, "cid-abc");

            String json = objectMapper.writeValueAsString(event);
            JsonNode message = objectMapper.readTree(json).get("message");

            assertThat(message.get("createdAt").asText()).isEqualTo("2026-06-11T10:00:00");
        }

        @Test
        @DisplayName("DmMessageEvent의 readAt(값 있을 때)이 ISO-8601 naive 문자열로 직렬화됨")
        void dmMessageEvent_readAt_isIso8601NaiveString_whenPresent() throws Exception {
            LocalDateTime createdAt = LocalDateTime.of(2026, 6, 11, 10, 0, 0);
            LocalDateTime readAt = LocalDateTime.of(2026, 6, 11, 10, 5, 0);
            DmMessageResponse msgResponse = new DmMessageResponse(
                    2L, "발신자", "내용", null, readAt, createdAt);
            DmMessageEvent event = DmMessageEvent.of(msgResponse, "cid-def");

            String json = objectMapper.writeValueAsString(event);
            JsonNode message = objectMapper.readTree(json).get("message");

            assertThat(message.get("readAt").asText()).isEqualTo("2026-06-11T10:05:00");
        }
    }

    @Nested
    @DisplayName("DmReadEvent 직렬화")
    class DmReadEventSerializationTest {

        @Test
        @DisplayName("DmReadEvent JSON 키가 스펙과 일치: type, readerNickname, readAt")
        void dmReadEvent_keyMatchesSpec() throws Exception {
            LocalDateTime readAt = LocalDateTime.of(2024, 1, 15, 11, 0, 0);
            DmReadEvent event = DmReadEvent.of("냥집사", readAt);

            String json = objectMapper.writeValueAsString(event);
            JsonNode node = objectMapper.readTree(json);

            assertThat(node.get("type").asText()).isEqualTo("READ");
            assertThat(node.has("readerNickname")).isTrue();
            assertThat(node.get("readerNickname").asText()).isEqualTo("냥집사");
            assertThat(node.has("readAt")).isTrue();
        }

        @Test
        @DisplayName("DmReadEvent의 readAt이 ISO-8601 naive 문자열로 직렬화됨")
        void dmReadEvent_readAt_isIso8601NaiveString() throws Exception {
            LocalDateTime readAt = LocalDateTime.of(2026, 6, 11, 10, 0, 0);
            DmReadEvent event = DmReadEvent.of("냥집사", readAt);

            String json = objectMapper.writeValueAsString(event);
            JsonNode node = objectMapper.readTree(json);

            assertThat(node.get("readAt").asText()).isEqualTo("2026-06-11T10:00:00");
        }
    }
}
