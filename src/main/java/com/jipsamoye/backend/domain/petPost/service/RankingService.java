package com.jipsamoye.backend.domain.petPost.service;

import com.jipsamoye.backend.domain.petPost.dto.response.RankingPageResponse;
import com.jipsamoye.backend.domain.petPost.entity.RankingPeriod;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface RankingService {

    /**
     * 기간별 좋아요 랭킹을 조회합니다.
     *
     * @param period WEEKLY | MONTHLY
     * @param date   기준 날짜 (속한 주/월의 범위를 서버가 계산). null이면 오늘
     * @param pageable 페이지 정보
     * @return 랭킹 결과 (period, startDate, endDate, isOngoing, posts)
     */
    RankingPageResponse getRanking(RankingPeriod period, LocalDate date, Pageable pageable);
}
