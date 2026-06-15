package com.jipsamoye.backend.loadtest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 더미 데이터 생성기 단위 테스트 (DB/Spring 없이 순수 로직 검증).
 */
class DummyDataGeneratorTest {

    @Test
    @DisplayName("제목 조합 수는 5억(1000만의 50배) 이상이다")
    void combination_count_exceeds_fifty_times_target() {
        long combinations = TitleGenerator.combinationCount();
        long target = 500_000_000L;
        assertThat(combinations)
                .as("조합 수=%,d (목표 5억=%,d 이상)", combinations, target)
                .isGreaterThanOrEqualTo(target);
    }

    @Test
    @DisplayName("모든 제목에 변별요소(숫자) 표현 슬롯이 실제로 삽입된다")
    void discriminator_slot_is_wired_in() {
        Random random = new Random(11L);
        for (int i = 0; i < 200_000; i++) {
            String title = TitleGenerator.generate(random);
            // 변별요소 슬롯의 표현이 제목에 들어가 있어야 한다(슬롯이 실제로 결선됨을 검증).
            // (100자 초과 잘림 시 끝부분 슬롯이 사라질 수 있으나, 본 생성기는 최대 길이가
            //  100자를 넘지 않게 설계되어 잘림이 발생하지 않는다.)
            boolean hasDiscriminator =
                    TitleGenerator.DISCRIMINATORS.stream().anyMatch(title::contains);
            assertThat(hasDiscriminator)
                    .as("변별요소 없음 title=%s", title)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("변별요소 슬롯은 자연스러운 숫자 표현으로 충분히 다양하다")
    void discriminator_pool_is_diverse() {
        // 변별요소 슬롯만으로 조합을 수백 배 늘리려면 종류가 충분해야 한다.
        long distinct = TitleGenerator.DISCRIMINATORS.stream().distinct().count();
        assertThat(distinct)
                .as("변별요소 종류=%d", distinct)
                .isEqualTo(TitleGenerator.DISCRIMINATORS.size())   // 중복 없음
                .isGreaterThanOrEqualTo(200);                      // 충분한 다양성
    }

    @Test
    @DisplayName("제목 길이는 항상 100자 이내다")
    void title_length_within_limit() {
        Random random = new Random(1L);
        for (int i = 0; i < 200_000; i++) {
            String title = TitleGenerator.generate(random);
            assertThat(title.length())
                    .as("title=%s", title)
                    .isLessThanOrEqualTo(TitleGenerator.MAX_TITLE_LENGTH);
            assertThat(title).isNotBlank();
        }
    }

    @Test
    @DisplayName("대량 생성(200만) 시 제목 중복률이 1% 미만이다")
    void title_low_duplication() {
        Random random = new Random(42L);
        int n = 2_000_000;
        Set<String> seen = new HashSet<>(n * 2);
        int duplicates = 0;
        for (int i = 0; i < n; i++) {
            if (!seen.add(TitleGenerator.generate(random))) {
                duplicates++;
            }
        }
        double dupRate = (double) duplicates / n;
        // 슬롯 조합 수가 5억 이상이라 200만 샘플의 중복률은 1% 미만이어야 한다.
        // (생일 문제 기준 기대 중복률 ≈ n/조합수 ≈ 0.4% 내외)
        assertThat(dupRate)
                .as("중복률=%.4f (duplicates=%d / %d)", dupRate, duplicates, n)
                .isLessThan(0.01);
    }

    @Test
    @DisplayName("검색 키워드(고양이/강아지/병원)가 목표 비율에 근사한다")
    void keyword_distribution_near_target() {
        Random random = new Random(7L);
        int n = 500_000;
        int cat = 0;
        int dog = 0;
        int hospital = 0;
        for (int i = 0; i < n; i++) {
            String title = TitleGenerator.generate(random);
            if (title.contains(TitleGenerator.KEYWORD_CAT)) cat++;
            if (title.contains(TitleGenerator.KEYWORD_DOG)) dog++;
            if (title.contains(TitleGenerator.KEYWORD_HOSPITAL)) hospital++;
        }
        double catRate = (double) cat / n;
        double dogRate = (double) dog / n;
        double hospRate = (double) hospital / n;

        // 목표 ± 1.5%p 허용오차 (대량 샘플이라 충분히 수렴)
        assertThat(catRate).isCloseTo(TitleGenerator.KEYWORD_CAT_RATIO, within(0.015));
        assertThat(dogRate).isCloseTo(TitleGenerator.KEYWORD_DOG_RATIO, within(0.015));
        assertThat(hospRate).isCloseTo(TitleGenerator.KEYWORD_HOSPITAL_RATIO, within(0.015));
    }

    @Test
    @DisplayName("nickname 은 10자 이내이며 1만 건 내에서 유니크하다")
    void nickname_unique_and_within_limit() {
        Set<String> seen = new HashSet<>();
        for (int i = 1; i <= DummyDataGenerator.USER_COUNT; i++) {
            String nickname = DummyDataGenerator.nickname(i);
            assertThat(nickname.length())
                    .as("nickname=%s", nickname)
                    .isLessThanOrEqualTo(10);
            assertThat(nickname).matches("[a-z0-9]+");
            assertThat(seen.add(nickname))
                    .as("중복 nickname=%s", nickname)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("like_count 멱법칙: 대부분 낮고 소수만 높다 (평균보다 큰 값이 소수)")
    void like_count_power_law() {
        Random random = new Random(3L);
        int n = 200_000;
        long sum = 0;
        int[] values = new int[n];
        int max = 0;
        for (int i = 0; i < n; i++) {
            int v = DummyDataGenerator.powerLaw(
                    random, DummyDataGenerator.MAX_LIKE, DummyDataGenerator.LIKE_POWER_EXPONENT);
            assertThat(v).isBetween(0, DummyDataGenerator.MAX_LIKE);
            values[i] = v;
            sum += v;
            max = Math.max(max, v);
        }
        double mean = (double) sum / n;

        // 멱법칙 특성 1: 평균(롱테일에 끌려 올라감)보다 작은 값이 다수(>70%)다.
        int belowMean = 0;
        for (int v : values) {
            if (v < mean) belowMean++;
        }
        assertThat((double) belowMean / n)
                .as("평균(%.1f) 미만 비율", mean)
                .isGreaterThan(0.70);

        // 멱법칙 특성 2: 평균은 최댓값의 극히 일부(대부분 낮음).
        assertThat(mean)
                .as("평균=%.1f, max=%d", mean, max)
                .isLessThan(DummyDataGenerator.MAX_LIKE * 0.20);

        // 멱법칙 특성 3: 일부는 높게 분포한다 (max 가 의미 있게 큼).
        assertThat(max).isGreaterThan(DummyDataGenerator.MAX_LIKE / 2);
    }

    @Test
    @DisplayName("image_urls 는 유효한 JSON 배열 문자열이며 빈 배열 비율이 목표에 근사한다")
    void image_urls_json_shape() {
        Random random = new Random(9L);
        int n = 200_000;
        int empty = 0;
        for (int i = 0; i < n; i++) {
            String json = DummyDataGenerator.imageUrlsJson(123L, random);
            assertThat(json).startsWith("[").endsWith("]");
            if (json.equals("[]")) {
                empty++;
            } else {
                assertThat(json).contains("https://images.jipsamoye.com/posts/123/");
                assertThat(json).contains(".jpg");
            }
        }
        assertThat((double) empty / n)
                .isCloseTo(DummyDataGenerator.EMPTY_IMAGE_RATIO, within(0.02));
    }

    @Test
    @DisplayName("TSV 이스케이프: 탭/개행/백슬래시는 이스케이프되고 따옴표/쉼표는 평문 유지")
    void tsv_escape_rules() {
        // 따옴표/쉼표는 ENCLOSED BY 미사용이므로 그대로 둬야 한다.
        assertThat(TsvWriter.escape("우리집 고양이, \"대박\"이에요"))
                .isEqualTo("우리집 고양이, \"대박\"이에요");
        // JSON 의 큰따옴표도 그대로
        assertThat(TsvWriter.escape("[\"https://a/b.jpg\"]"))
                .isEqualTo("[\"https://a/b.jpg\"]");
        // 탭/개행/백슬래시는 이스케이프
        assertThat(TsvWriter.escape("a\tb")).isEqualTo("a\\tb");
        assertThat(TsvWriter.escape("a\nb")).isEqualTo("a\\nb");
        assertThat(TsvWriter.escape("a\\b")).isEqualTo("a\\\\b");
        assertThat(TsvWriter.escape("a\r\nb")).isEqualTo("a\\r\\nb");
    }

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
