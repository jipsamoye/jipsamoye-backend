package com.jipsamoye.backend.domain.petPost.service;

import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.petPost.dto.response.RankingPageResponse;
import com.jipsamoye.backend.domain.petPost.entity.RankingPeriod;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingServiceImpl implements RankingService {

    private final PetPostRepository petPostRepository;

    @Override
    public RankingPageResponse getRanking(RankingPeriod period, LocalDate date, Pageable pageable) {
        LocalDate baseDate = (date != null) ? date : LocalDate.now();

        LocalDate startDate;
        LocalDate endDateExclusive;

        if (period == RankingPeriod.WEEKLY) {
            // ISO 주: 월요일 시작
            startDate = baseDate.with(DayOfWeek.MONDAY);
            endDateExclusive = startDate.plusWeeks(1);
        } else {
            // MONTHLY: 1일 ~ 다음 달 1일
            startDate = baseDate.withDayOfMonth(1);
            endDateExclusive = startDate.plusMonths(1);
        }

        // 미래 기간 검증: startDate > today
        LocalDate today = LocalDate.now();
        if (startDate.isAfter(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "미래 기간은 조회할 수 없습니다.");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDateExclusive.atStartOfDay();

        // isOngoing: start <= today < endExclusive
        boolean isOngoing = !startDate.isAfter(today) && today.isBefore(endDateExclusive);

        // UI 표시용 포함 종료일 = exclusive end - 1일
        LocalDate endDateInclusive = endDateExclusive.minusDays(1);

        Page<PetPostListResponse> page = petPostRepository.findRanking(start, end, pageable)
                .map(PetPostListResponse::from);

        PageResponse<PetPostListResponse> pageResponse = PageResponse.from(page);

        return new RankingPageResponse(period, startDate, endDateInclusive, isOngoing, pageResponse);
    }
}
