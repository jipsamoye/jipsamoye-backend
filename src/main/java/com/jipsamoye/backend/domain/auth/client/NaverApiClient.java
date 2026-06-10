package com.jipsamoye.backend.domain.auth.client;

import com.jipsamoye.backend.domain.auth.client.dto.response.NaverProfileResponse;
import com.jipsamoye.backend.domain.auth.client.dto.response.NaverTokenResponse;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 네이버 OAuth2 API 연동 클라이언트.
 * <p>
 * 생성자에서 {@link RestClient.Builder}를 주입받아 타임아웃 설정 후 {@code build()}한다.
 * MockRestServiceServer 테스트는 {@link #NaverApiClient(RestClient, String, String)} 생성자를 사용한다.
 */
@Component
public class NaverApiClient {

    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String PROFILE_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    /**
     * 운영용 생성자 — Spring이 주입하는 {@link RestClient.Builder}에 타임아웃을 설정한다.
     * MockRestServiceServer 테스트에서는 이 생성자를 사용하지 않는다.
     */
    @Autowired
    public NaverApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${naver.oauth.client-id}") String clientId,
            @Value("${naver.oauth.client-secret}") String clientSecret
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);

        this.restClient = restClientBuilder
                .requestFactory(factory)
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * 테스트용 생성자 — MockRestServiceServer가 바인딩된 {@link RestClient}를 직접 주입받는다.
     */
    NaverApiClient(RestClient restClient, String clientId, String clientSecret) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * 인가 코드를 액세스 토큰으로 교환한다.
     * 네이버는 실패 시에도 HTTP 200 + error 바디를 반환하므로 바디의 error 필드를 반드시 검사한다.
     *
     * @throws BusinessException NAVER_TOKEN_EXCHANGE_FAILED — 코드 무효·만료
     * @throws BusinessException NAVER_API_ERROR             — 네트워크·서버 오류
     */
    public NaverTokenResponse exchangeToken(String code, String state) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("state", state);

        try {
            NaverTokenResponse response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(NaverTokenResponse.class);

            if (response == null || response.isError()) {
                throw new BusinessException(ErrorCode.NAVER_TOKEN_EXCHANGE_FAILED);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.NAVER_API_ERROR);
        }
    }

    /**
     * 액세스 토큰으로 네이버 사용자 프로필을 조회한다.
     *
     * @throws BusinessException NAVER_API_ERROR — resultcode 불일치·response null·네트워크 오류
     */
    public NaverProfileResponse getProfile(String accessToken) {
        try {
            NaverProfileResponse response = restClient.get()
                    .uri(PROFILE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(NaverProfileResponse.class);

            if (response == null || !response.isSuccess()) {
                throw new BusinessException(ErrorCode.NAVER_API_ERROR);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.NAVER_API_ERROR);
        }
    }
}
