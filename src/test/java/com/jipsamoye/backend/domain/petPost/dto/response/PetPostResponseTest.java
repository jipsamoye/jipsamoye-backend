package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PetPostResponseTest {

    private static User normalUser() {
        return User.builder()
                .nickname("테스터")
                .profileImageUrl("https://cdn.example.com/me.jpg")
                .email("tester@example.com")
                .provider(Provider.KAKAO)
                .providerId("provider-123")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("일반 게시글이면 aiGenerated는 false다")
    void from_normalPost_aiGeneratedFalse() {
        PetPost post = PetPost.builder()
                .user(normalUser())
                .title("우리집 강아지")
                .content("귀여움")
                .imageUrls(List.of("https://cdn.example.com/img1.jpg"))
                .build();

        PetPostResponse response = PetPostResponse.from(post);

        assertThat(response.aiGenerated()).isFalse();
        assertThat(response.nickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("AI 자동 생성 게시글이면 aiGenerated는 true다")
    void from_aiGeneratedPost_aiGeneratedTrue() {
        PetPost post = PetPost.builder()
                .user(normalUser())
                .title("AI 키캡 자랑")
                .imageUrls(List.of("https://cdn.example.com/figurine.jpg"))
                .aiGenerated(true)
                .build();

        PetPostResponse response = PetPostResponse.from(post);

        assertThat(response.aiGenerated()).isTrue();
    }

    @Test
    @DisplayName("탈퇴 유저의 게시글이어도 aiGenerated는 그대로 유지된다")
    void from_deletedUser_keepsAiGenerated() {
        User user = normalUser();
        user.softDelete();
        PetPost post = PetPost.builder()
                .user(user)
                .title("AI 키캡 자랑")
                .imageUrls(List.of("https://cdn.example.com/figurine.jpg"))
                .aiGenerated(true)
                .build();

        PetPostResponse response = PetPostResponse.from(post);

        assertThat(response.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.aiGenerated()).isTrue();
    }
}
