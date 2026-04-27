package com.jipsamoye.backend.domain.petPost.dto.response;

import com.jipsamoye.backend.domain.petPost.entity.RankingPeriod;
import com.jipsamoye.backend.global.response.PageResponse;

import java.time.LocalDate;

/**
 * 랭킹 API 응답 wrapper.
 * endDate는 exclusive end - 1일 (UI 표시용 포함 종료일).
 * 예) WEEKLY: start=04.21(월) → end(exclusive)=04.28 → endDate=04.27(일)
 */
public record RankingPageResponse(
        RankingPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        boolean isOngoing,
        PageResponse<PetPostListResponse> posts
) {
}
