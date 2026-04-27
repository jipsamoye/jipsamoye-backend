package com.jipsamoye.backend.domain.petPost.service;

import com.jipsamoye.backend.domain.petPost.dto.response.RankingPageResponse;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.entity.RankingPeriod;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceImplTest {

    @InjectMocks
    private RankingServiceImpl rankingService;

    @Mock
    private PetPostRepository petPostRepository;

    private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 20);

    private Page<PetPost> emptyPage() {
        return new PageImpl<>(List.of(), DEFAULT_PAGEABLE, 0);
    }

    @Test
    @DisplayName("WEEKLY - 월요일(2026-04-20) 기준: startDate=04.20(월), endDate=04.26(일)")
    void getRanking_weekly_monday() {
        LocalDate monday = LocalDate.of(2026, 4, 20);
        LocalDateTime expectedStart = LocalDateTime.of(2026, 4, 20, 0, 0, 0);
        LocalDateTime expectedEnd = LocalDateTime.of(2026, 4, 27, 0, 0, 0);

        when(petPostRepository.findRanking(eq(expectedStart), eq(expectedEnd), any(Pageable.class)))
                .thenReturn(emptyPage());

        RankingPageResponse response = rankingService.getRanking(RankingPeriod.WEEKLY, monday, DEFAULT_PAGEABLE);

        assertThat(response.period()).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 4, 26));
    }

    @Test
    @DisplayName("WEEKLY - 일요일(2026-04-26) 기준: 직전 월요일(04.20)이 startDate")
    void getRanking_weekly_sunday() {
        // 2026-04-26은 일요일 → 직전 월요일 04.20이 start
        LocalDate sunday = LocalDate.of(2026, 4, 26);
        LocalDateTime expectedStart = LocalDateTime.of(2026, 4, 20, 0, 0, 0);
        LocalDateTime expectedEnd = LocalDateTime.of(2026, 4, 27, 0, 0, 0);

        when(petPostRepository.findRanking(eq(expectedStart), eq(expectedEnd), any(Pageable.class)))
                .thenReturn(emptyPage());

        RankingPageResponse response = rankingService.getRanking(RankingPeriod.WEEKLY, sunday, DEFAULT_PAGEABLE);

        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 4, 26));
    }

    @Test
    @DisplayName("MONTHLY - 달 마지막날(2026-03-31) 기준: startDate=03.01, endDate=03.31")
    void getRanking_monthly_lastDayOfMonth() {
        LocalDate lastDay = LocalDate.of(2026, 3, 31);
        LocalDateTime expectedStart = LocalDateTime.of(2026, 3, 1, 0, 0, 0);
        LocalDateTime expectedEnd = LocalDateTime.of(2026, 4, 1, 0, 0, 0);

        when(petPostRepository.findRanking(eq(expectedStart), eq(expectedEnd), any(Pageable.class)))
                .thenReturn(emptyPage());

        RankingPageResponse response = rankingService.getRanking(RankingPeriod.MONTHLY, lastDay, DEFAULT_PAGEABLE);

        assertThat(response.period()).isEqualTo(RankingPeriod.MONTHLY);
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("MONTHLY - 윤년 2월(2024-02-15) 기준: startDate=02.01, endDate=02.29(윤년)")
    void getRanking_monthly_leapYearFebruary() {
        // 2024년은 윤년 → 2월 = 29일
        LocalDate leapFeb = LocalDate.of(2024, 2, 15);
        LocalDateTime expectedStart = LocalDateTime.of(2024, 2, 1, 0, 0, 0);
        LocalDateTime expectedEnd = LocalDateTime.of(2024, 3, 1, 0, 0, 0);

        when(petPostRepository.findRanking(eq(expectedStart), eq(expectedEnd), any(Pageable.class)))
                .thenReturn(emptyPage());

        RankingPageResponse response = rankingService.getRanking(RankingPeriod.MONTHLY, leapFeb, DEFAULT_PAGEABLE);

        assertThat(response.startDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("미래 기간(startDate > today) 요청 시 BusinessException 발생")
    void getRanking_futureDate_throwsBusinessException() {
        LocalDate futureDate = LocalDate.now().plusMonths(2);

        assertThatThrownBy(() -> rankingService.getRanking(RankingPeriod.WEEKLY, futureDate, DEFAULT_PAGEABLE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("미래 기간은 조회할 수 없습니다.");
    }

    @Test
    @DisplayName("isOngoing - 현재 진행 중인 주간은 true")
    void getRanking_weekly_isOngoing_true() {
        LocalDate today = LocalDate.now();

        when(petPostRepository.findRanking(any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        RankingPageResponse response = rankingService.getRanking(RankingPeriod.WEEKLY, today, DEFAULT_PAGEABLE);

        assertThat(response.isOngoing()).isTrue();
    }

    @Test
    @DisplayName("isOngoing - 과거 주간은 false")
    void getRanking_weekly_pastWeek_isOngoing_false() {
        // 2주 전 날짜 → 해당 주는 이미 종료
        LocalDate twoWeeksAgo = LocalDate.now().minusWeeks(2);

        when(petPostRepository.findRanking(any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        RankingPageResponse response = rankingService.getRanking(RankingPeriod.WEEKLY, twoWeeksAgo, DEFAULT_PAGEABLE);

        assertThat(response.isOngoing()).isFalse();
    }
}
