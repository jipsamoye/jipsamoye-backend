package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PetPostListResponseTest {

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

    private static PetPost postBy(User user) {
        return PetPost.builder()
                .user(user)
                .title("우리집 강아지")
                .content("귀여움")
                .imageUrls(List.of("https://cdn.example.com/img1.jpg"))
                .build();
    }

    @Test
    @DisplayName("정상 유저의 게시글이면 nickname과 profileImageUrl이 그대로 채워진다")
    void from_normalUser_fillsProfileFields() {
        PetPostListResponse response = PetPostListResponse.from(postBy(normalUser()));

        assertThat(response.nickname()).isEqualTo("테스터");
        assertThat(response.profileImageUrl()).isEqualTo("https://cdn.example.com/me.jpg");
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example.com/img1.jpg");
        assertThat(response.commentCount()).isZero();
    }

    @Test
    @DisplayName("탈퇴 유저의 게시글이면 nickname은 마스킹되고 profileImageUrl은 null이다")
    void from_deletedUser_masksNicknameAndNullsProfileImage() {
        User user = normalUser();
        user.softDelete();

        PetPostListResponse response = PetPostListResponse.from(postBy(user));

        assertThat(response.nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("정상 유저인데 프로필 이미지가 없으면 profileImageUrl은 null이다")
    void from_normalUserWithoutProfileImage_returnsNull() {
        User user = User.builder()
                .nickname("이미지없음")
                .email("noimg@example.com")
                .provider(Provider.GOOGLE)
                .providerId("provider-456")
                .role(Role.USER)
                .build();

        PetPostListResponse response = PetPostListResponse.from(postBy(user));

        assertThat(response.nickname()).isEqualTo("이미지없음");
        assertThat(response.profileImageUrl()).isNull();
    }
}
