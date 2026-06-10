package com.jipsamoye.backend.domain.auth.client;

import com.jipsamoye.backend.domain.auth.client.dto.response.NaverProfileResponse;
import com.jipsamoye.backend.domain.auth.client.dto.response.NaverTokenResponse;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * NaverApiClient 단위 테스트.
 * MockRestServiceServer를 사용하므로 테스트용 생성자({@link NaverApiClient#NaverApiClient(RestClient, String, String)})를 사용한다.
 */
class NaverApiClientTest {

    private MockRestServiceServer mockServer;
    private NaverApiClient naverApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        // 테스트용 생성자: MockRestServiceServer가 바인딩된 RestClient를 직접 사용
        naverApiClient = new NaverApiClient(builder.build(), "test-client-id", "test-client-secret");
    }

    @Nested
    @DisplayName("exchangeToken")
    class ExchangeTokenTest {

        @Test
        @DisplayName("토큰 교환 성공 - form 파라미터 전송 후 access_token 파싱")
        void success() {
            mockServer.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=test-client-id")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=test-client-secret")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("code=test-code")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("state=test-state")))
                    .andRespond(withSuccess("""
                            {
                                "access_token": "AAAAwK8g1VfS",
                                "refresh_token": "c8ceMEJisO4Se",
                                "token_type": "bearer",
                                "expires_in": "3600"
                            }
                            """, MediaType.APPLICATION_JSON));

            NaverTokenResponse result = naverApiClient.exchangeToken("test-code", "test-state");

            assertThat(result.accessToken()).isEqualTo("AAAAwK8g1VfS");
            assertThat(result.tokenType()).isEqualTo("bearer");
            assertThat(result.expiresIn()).isEqualTo("3600");
            mockServer.verify();
        }

        @Test
        @DisplayName("HTTP 200 + error 바디 → NAVER_TOKEN_EXCHANGE_FAILED")
        void errorBody() {
            mockServer.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("""
                            {
                                "error": "invalid_request",
                                "error_description": "no valid data in session"
                            }
                            """, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> naverApiClient.exchangeToken("bad-code", "state"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NAVER_TOKEN_EXCHANGE_FAILED));
            mockServer.verify();
        }

        @Test
        @DisplayName("5xx 응답 → NAVER_API_ERROR")
        void serverError() {
            mockServer.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withServerError());

            assertThatThrownBy(() -> naverApiClient.exchangeToken("code", "state"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NAVER_API_ERROR));
            mockServer.verify();
        }
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfileTest {

        @Test
        @DisplayName("프로필 조회 성공 - Authorization Bearer 헤더 검증 + id/nickname/email 파싱")
        void success() {
            mockServer.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"))
                    .andRespond(withSuccess("""
                            {
                                "resultcode": "00",
                                "message": "success",
                                "response": {
                                    "id": "naver-user-id-123",
                                    "nickname": "테스트유저",
                                    "email": "test@naver.com",
                                    "profile_image": "https://ssl.pstatic.net/profile.jpg"
                                }
                            }
                            """, MediaType.APPLICATION_JSON));

            NaverProfileResponse result = naverApiClient.getProfile("test-access-token");

            assertThat(result.resultcode()).isEqualTo("00");
            assertThat(result.response().id()).isEqualTo("naver-user-id-123");
            assertThat(result.response().nickname()).isEqualTo("테스트유저");
            assertThat(result.response().email()).isEqualTo("test@naver.com");
            assertThat(result.response().profileImage()).isEqualTo("https://ssl.pstatic.net/profile.jpg");
            mockServer.verify();
        }

        @Test
        @DisplayName("resultcode != 00 → NAVER_API_ERROR")
        void resultcodeFailure() {
            mockServer.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {
                                "resultcode": "024",
                                "message": "Unauthorized"
                            }
                            """, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> naverApiClient.getProfile("invalid-token"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NAVER_API_ERROR));
            mockServer.verify();
        }

        @Test
        @DisplayName("4xx 응답 → NAVER_API_ERROR")
        void networkError() {
            mockServer.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withUnauthorizedRequest());

            assertThatThrownBy(() -> naverApiClient.getProfile("expired-token"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NAVER_API_ERROR));
            mockServer.verify();
        }
    }
}
