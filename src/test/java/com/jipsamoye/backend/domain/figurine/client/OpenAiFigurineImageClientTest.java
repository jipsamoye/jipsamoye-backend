package com.jipsamoye.backend.domain.figurine.client;

import com.jipsamoye.backend.domain.figurine.config.OpenAiProperties;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiFigurineImageClientTest {

    private static final String EDITS_URL = "https://api.openai.com/v1/images/edits";
    private static final OpenAiProperties PROPERTIES =
            new OpenAiProperties("test-api-key", "gpt-image-1", "1024x1024", "medium");

    private MockRestServiceServer mockServer;
    private OpenAiFigurineImageClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiFigurineImageClient(builder.build(), PROPERTIES);
    }

    @Test
    @DisplayName("정상 응답이면 b64_json을 디코드한 이미지 바이트를 반환한다")
    void generateKeycapImage_success() {
        byte[] expected = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
        String b64 = Base64.getEncoder().encodeToString(expected);
        mockServer.expect(requestTo(EDITS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("""
                        { "created": 1721600000, "data": [ { "b64_json": "%s" } ] }
                        """.formatted(b64), MediaType.APPLICATION_JSON));

        byte[] result = client.generateKeycapImage(
                "source".getBytes(StandardCharsets.UTF_8), "image/webp", "source.webp");

        assertThat(result).isEqualTo(expected);
        mockServer.verify();
    }

    @Test
    @DisplayName("응답 data가 비어 있으면 FIGURINE_GENERATION_FAILED 예외를 던진다")
    void generateKeycapImage_emptyData_throws() {
        mockServer.expect(requestTo(EDITS_URL))
                .andRespond(withSuccess("""
                        { "created": 1721600000, "data": [] }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateKeycapImage(
                "source".getBytes(StandardCharsets.UTF_8), "image/webp", "source.webp"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_GENERATION_FAILED));
    }

    @Test
    @DisplayName("서버 오류 응답이면 FIGURINE_GENERATION_FAILED 예외를 던진다")
    void generateKeycapImage_serverError_throws() {
        mockServer.expect(requestTo(EDITS_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.generateKeycapImage(
                "source".getBytes(StandardCharsets.UTF_8), "image/webp", "source.webp"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_GENERATION_FAILED));
    }
}
