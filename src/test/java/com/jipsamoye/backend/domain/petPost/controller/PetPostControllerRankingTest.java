package com.jipsamoye.backend.domain.petPost.controller;

import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.petPost.dto.response.RankingPageResponse;
import com.jipsamoye.backend.domain.petPost.entity.RankingPeriod;
import com.jipsamoye.backend.domain.petPost.service.PetPostService;
import com.jipsamoye.backend.domain.petPost.service.RankingService;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import com.jipsamoye.backend.global.response.SliceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = PetPostController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class PetPostControllerRankingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PetPostService petPostService;

    @MockitoBean
    private RankingService rankingService;

    private RankingPageResponse sampleResponse() {
        PageResponse<PetPostListResponse> posts = PageResponse.from(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)
        );
        return new RankingPageResponse(
                RankingPeriod.WEEKLY,
                LocalDate.of(2026, 4, 20),
                LocalDate.of(2026, 4, 26),
                true,
                posts
        );
    }

    @Test
    @DisplayName("200 - WEEKLY 랭킹 정상 조회")
    void getRanking_weekly_returns200() throws Exception {
        when(rankingService.getRanking(eq(RankingPeriod.WEEKLY), any(), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/posts/ranking")
                        .param("period", "WEEKLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.period").value("WEEKLY"))
                .andExpect(jsonPath("$.data.startDate").value("2026-04-20"))
                .andExpect(jsonPath("$.data.endDate").value("2026-04-26"))
                .andExpect(jsonPath("$.data.isOngoing").value(true));
    }

    @Test
    @DisplayName("200 - MONTHLY 랭킹 정상 조회")
    void getRanking_monthly_returns200() throws Exception {
        PageResponse<PetPostListResponse> posts = PageResponse.from(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)
        );
        RankingPageResponse monthlyResponse = new RankingPageResponse(
                RankingPeriod.MONTHLY,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                true,
                posts
        );

        when(rankingService.getRanking(eq(RankingPeriod.MONTHLY), any(), any()))
                .thenReturn(monthlyResponse);

        mockMvc.perform(get("/api/posts/ranking")
                        .param("period", "MONTHLY")
                        .param("date", "2026-04-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("MONTHLY"));
    }

    @Test
    @DisplayName("400 - period 파라미터 누락")
    void getRanking_periodMissing_returns400() throws Exception {
        mockMvc.perform(get("/api/posts/ranking"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("400 - period 값이 잘못된 경우 (INVALID_PERIOD)")
    void getRanking_invalidPeriod_returns400() throws Exception {
        mockMvc.perform(get("/api/posts/ranking")
                        .param("period", "INVALID_PERIOD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("400 - date 형식이 잘못된 경우 (not YYYY-MM-DD)")
    void getRanking_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/posts/ranking")
                        .param("period", "WEEKLY")
                        .param("date", "20260420"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("400 - 미래 기간 요청 시 BusinessException -> 400")
    void getRanking_futurePeriod_returns400() throws Exception {
        when(rankingService.getRanking(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "미래 기간은 조회할 수 없습니다."));

        mockMvc.perform(get("/api/posts/ranking")
                        .param("period", "WEEKLY")
                        .param("date", "2099-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("미래 기간은 조회할 수 없습니다."));
    }

    private SliceResponse<PetPostListResponse> emptySlice() {
        return SliceResponse.from(new SliceImpl<>(List.of(), PageRequest.of(0, 20), false));
    }

    @Test
    @DisplayName("200 - 검색 정상 조회 (page/size 기본값)")
    void searchPosts_returns200() throws Exception {
        when(petPostService.searchPosts(anyString(), anyInt(), anyInt()))
                .thenReturn(emptySlice());

        mockMvc.perform(get("/api/posts/search")
                        .param("q", "강아지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("400 - 검색 page가 음수인 경우 (@Min(0) 위반)")
    void searchPosts_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/api/posts/search")
                        .param("q", "강아지")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("400 - 검색 size가 0인 경우 (@Min(1) 위반)")
    void searchPosts_zeroSize_returns400() throws Exception {
        mockMvc.perform(get("/api/posts/search")
                        .param("q", "강아지")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("400 - 검색 size가 50 초과인 경우 (@Max(50) 위반)")
    void searchPosts_oversizeSize_returns400() throws Exception {
        mockMvc.perform(get("/api/posts/search")
                        .param("q", "강아지")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
