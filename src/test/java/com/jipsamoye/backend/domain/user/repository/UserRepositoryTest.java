package com.jipsamoye.backend.domain.user.repository;

import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("datajpatest")
@DisplayName("UserRepository.searchByNickname - 닉네임 검색 (본인·탈퇴 제외, 정렬)")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User me;

    @BeforeEach
    void setUp() {
        me = userRepository.save(newUser("나검색자", "me@test.com", "pid-me"));
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

    @Test
    @DisplayName("부분일치하는 유저를 찾는다")
    void searchByNickname_partialMatch() {
        userRepository.save(newUser("cathy", "c1@test.com", "p1"));
        userRepository.save(newUser("dogman", "d1@test.com", "p2"));

        Page<User> result = userRepository.searchByNickname("cat", "cat", me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(User::getNickname).containsExactly("cathy");
    }

    @Test
    @DisplayName("본인은 결과에서 제외된다")
    void searchByNickname_excludesSelf() {
        userRepository.save(newUser("나친구", "f@test.com", "pf"));

        Page<User> result = userRepository.searchByNickname("나", "나", me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(User::getNickname)
                .containsExactly("나친구")
                .doesNotContain("나검색자");
    }

    @Test
    @DisplayName("탈퇴 유저는 결과에서 제외된다")
    void searchByNickname_excludesDeleted() {
        User active = userRepository.save(newUser("cat활성", "a@test.com", "pa"));
        User deleted = newUser("cat탈퇴", "del@test.com", "pd");
        deleted.softDelete();
        userRepository.save(deleted);

        Page<User> result = userRepository.searchByNickname("cat", "cat", me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(User::getNickname)
                .containsExactly("cat활성")
                .doesNotContain("cat탈퇴");
    }

    @Test
    @DisplayName("대소문자를 무시하고 매칭한다")
    void searchByNickname_caseInsensitive() {
        userRepository.save(newUser("CatLover", "cl@test.com", "pcl"));

        Page<User> result = userRepository.searchByNickname("catlover", "catlover", me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(User::getNickname).containsExactly("CatLover");
    }

    @Test
    @DisplayName("정렬: 정확 일치 > 접두 일치 > 부분 일치 순")
    void searchByNickname_orderByMatchType() {
        // 닉네임은 length 10 제약. 'cat' 기준으로 정확/접두/부분 케이스 구성
        userRepository.save(newUser("scatter", "s@test.com", "ps"));  // 부분
        userRepository.save(newUser("cat", "e@test.com", "pe"));      // 정확
        userRepository.save(newUser("cathy", "p@test.com", "pp"));    // 접두

        Page<User> result = userRepository.searchByNickname("cat", "cat", me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(User::getNickname)
                .containsExactly("cat", "cathy", "scatter");
    }

    @Test
    @DisplayName("같은 매칭 등급 내에서는 닉네임 오름차순 정렬")
    void searchByNickname_tieBreakByNicknameAsc() {
        userRepository.save(newUser("catB", "b@test.com", "pb"));
        userRepository.save(newUser("catA", "a@test.com", "pa2"));

        Page<User> result = userRepository.searchByNickname("cat", "cat", me.getId(), PageRequest.of(0, 20));

        // 둘 다 접두 일치(등급 1), 닉네임 ASC → catA, catB
        assertThat(result.getContent()).extracting(User::getNickname)
                .containsExactly("catA", "catB");
    }

    @Test
    @DisplayName("페이징: totalElements와 페이지 분할이 동작한다")
    void searchByNickname_paging() {
        List<String> names = List.of("cat1", "cat2", "cat3");
        for (int i = 0; i < names.size(); i++) {
            userRepository.save(newUser(names.get(i), "pg" + i + "@test.com", "ppg" + i));
        }

        Page<User> firstPage = userRepository.searchByNickname("cat", "cat", me.getId(), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();

        Page<User> secondPage = userRepository.searchByNickname("cat", "cat", me.getId(), PageRequest.of(1, 2));
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("LIKE 와일드카드 이스케이프: '%' 검색은 전체 유저가 아니라 literal '%' 포함 유저만 매칭")
    void searchByNickname_escapesPercentWildcard() {
        // q="%"는 이스케이프 전이라면 LIKE '%%%'로 전체 매칭. 서비스가 "%"를 "\%"로 이스케이프해
        // 넘기면 literal '%'를 가진 닉네임만 매칭되어야 한다.
        userRepository.save(newUser("cathy", "c1@test.com", "p1"));
        userRepository.save(newUser("dogman", "d1@test.com", "p2"));
        User withPercent = userRepository.save(newUser("a%b", "pct@test.com", "ppct"));

        // 서비스의 escapeLikePattern("%") == "\%", exact 비교는 원본 "%"
        Page<User> result = userRepository.searchByNickname("\\%", "%", me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(User::getNickname)
                .containsExactly(withPercent.getNickname())
                .doesNotContain("cathy", "dogman", "나검색자");
    }

    @Test
    @DisplayName("LIKE 와일드카드 이스케이프: literal '%' 미포함 유저만 있으면 '%' 검색은 빈 결과")
    void searchByNickname_percentNoMatch_returnsEmpty() {
        userRepository.save(newUser("cathy", "c1@test.com", "p1"));
        userRepository.save(newUser("dogman", "d1@test.com", "p2"));

        Page<User> result = userRepository.searchByNickname("\\%", "%", me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }
}
