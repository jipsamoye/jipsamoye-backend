package com.jipsamoye.backend.domain.figurine.client;

import com.jipsamoye.backend.domain.figurine.client.dto.response.OpenAiImageResponse;
import com.jipsamoye.backend.domain.figurine.config.OpenAiProperties;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;

@Component
public class OpenAiFigurineImageClient implements FigurineImageClient {

    private static final String EDITS_URL = "https://api.openai.com/v1/images/edits";
    private static final String KEYCAP_PROMPT = """
            Transform the pet in this photo into an adorable chibi-style miniature figurine \
            sculpted on top of an artisan mechanical keyboard keycap. \
            Product photography style, soft studio lighting, glossy resin texture, \
            keyboard visible blurred in the background.""";

    private final RestClient restClient;
    private final OpenAiProperties properties;

    @Autowired
    public OpenAiFigurineImageClient(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.properties = properties;
    }

    OpenAiFigurineImageClient(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public byte[] generateKeycapImage(byte[] sourceImage, String contentType, String filename) {
        try {
            OpenAiImageResponse response = restClient.post()
                    .uri(EDITS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(buildMultipartBody(sourceImage, contentType, filename))
                    .retrieve()
                    .body(OpenAiImageResponse.class);

            if (response == null || !response.hasImage()) {
                throw new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "OpenAI 응답에 이미지가 없습니다.");
            }
            return Base64.getDecoder().decode(response.firstB64Json());
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "OpenAI API 호출 실패: " + e.getMessage());
        }
    }

    private MultiValueMap<String, Object> buildMultipartBody(byte[] sourceImage, String contentType, String filename) {
        ByteArrayResource imageResource = new ByteArrayResource(sourceImage) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(contentType));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new HttpEntity<>(imageResource, imageHeaders));
        body.add("model", properties.model());
        body.add("prompt", KEYCAP_PROMPT);
        body.add("size", properties.size());
        body.add("quality", properties.quality());
        body.add("n", "1");
        return body;
    }
}
