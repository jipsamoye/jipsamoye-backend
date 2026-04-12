package com.jipsamoye.backend.global.dummy;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Tag(name = "Dummy", description = "테스트 더미 데이터 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dummy")
public class DummyDataController {

    private final UserRepository userRepository;
    private final PetPostRepository petPostRepository;

    private static final String[] TITLES = {
            "우리 강아지 산책 중 찍은 사진",
            "오늘의 멍멍이 일상",
            "귀여운 포즈 자랑합니다",
            "간식 앞에서 앉아 성공!",
            "산책하다가 만난 친구",
            "낮잠 자는 모습이 너무 귀여워요",
            "목욕 후 뽀송뽀송한 우리 아이",
            "공원에서 신나게 뛰어놀기",
            "우리집 댕댕이 자랑 좀 할게요",
            "오늘도 귀여운 하루",
            "강아지랑 카페 데이트",
            "아침 산책은 역시 최고",
            "새 장난감에 신난 우리 강아지",
            "비 오는 날 창밖 구경 중",
            "간식 달라고 눈빛 공격 중",
            "우리 강아지 첫 미용 후기",
            "함께하는 주말 나들이",
            "잠든 모습이 천사 같아요",
            "오늘 특별히 멋진 포즈",
            "우리 아이 생일 파티!"
    };

    private static final String[] CONTENTS = {
            "오늘 날씨가 좋아서 산책 다녀왔어요! 기분 최고~",
            "간식 달라고 애교 부리는 중이에요 ㅋㅋ",
            "이 표정 보세요 ㅋㅋㅋ 너무 웃겨요",
            "매일매일 귀여운 우리 강아지 자랑합니다!",
            "오늘도 열심히 놀고 푹 자는 중이에요",
            "산책하면서 다른 강아지 친구도 만났어요!",
            "목욕을 싫어하지만 결과는 항상 만족 ㅎㅎ",
            "공원에서 뛰어노는 모습이 너무 행복해 보여요",
            "새로 산 장난감에 완전 빠졌어요!",
            "비 오는 날에는 집에서 뒹굴뒹굴~",
            "카페에서도 얌전하게 잘 있어줘서 고마워요",
            "아침 산책 후 간식 타임! 오늘도 잘 먹었어요",
            "미용하고 왔더니 완전 다른 강아지 같아요 ㅋㅋ",
            "이렇게 귀여운 눈빛에 매번 넘어갑니다...",
            "주말에 같이 나들이 다녀왔어요! 즐거웠다~",
            "잠든 모습 보면 하루의 피로가 싹 사라져요",
            "사진 찍으려고 하면 항상 딴 데를 봐요 ㅋㅋ",
            "오늘은 특별한 날! 생일 축하해 우리 아이~",
            "같이 있는 시간이 제일 행복해요 💕",
            "이 귀여움을 저만 보기 아까워서 공유합니다!"
    };

    @Operation(summary = "더미 데이터 생성", description = "테스트용 유저 1명 + 게시글 20개를 생성합니다.")
    @GetMapping("/init")
    @Transactional
    public ResponseEntity<ApiResponse<String>> initDummyData() {
        Random random = new Random();
        RestTemplate restTemplate = new RestTemplate();

        // 유저 1명 생성
        User user = User.builder()
                .nickname("테스트유저")
                .bio("더미 데이터 테스트용 계정입니다.")
                .email("dummy@test.com")
                .provider(Provider.KAKAO)
                .providerId("dummy-" + System.currentTimeMillis())
                .role(Role.USER)
                .build();
        userRepository.save(user);
        log.info("더미 유저 생성 완료: {}", user.getId());

        // 게시글 20개 생성
        List<Integer> titleIndices = new ArrayList<>();
        for (int i = 0; i < TITLES.length; i++) titleIndices.add(i);
        Collections.shuffle(titleIndices);

        for (int i = 0; i < 20; i++) {
            // 랜덤 강아지 이미지 1~3장
            int imageCount = random.nextInt(3) + 1;
            List<String> imageUrls = new ArrayList<>();
            for (int j = 0; j < imageCount; j++) {
                try {
                    Map<String, Object> response = restTemplate.getForObject(
                            "https://dog.ceo/api/breeds/image/random", Map.class);
                    if (response != null && "success".equals(response.get("status"))) {
                        imageUrls.add((String) response.get("message"));
                    }
                } catch (Exception e) {
                    log.warn("강아지 이미지 가져오기 실패, placeholder 사용", e);
                    imageUrls.add("https://placehold.co/600x400?text=Dog+" + (j + 1));
                }
            }

            PetPost post = PetPost.builder()
                    .user(user)
                    .title(TITLES[titleIndices.get(i)])
                    .content(CONTENTS[random.nextInt(CONTENTS.length)])
                    .imageUrls(imageUrls)
                    .build();
            petPostRepository.save(post);
        }

        log.info("더미 게시글 20개 생성 완료");
        return ResponseEntity.ok(ApiResponse.<String>success("더미 데이터 생성 완료", "유저 1명 + 게시글 20개 생성"));
    }
}
