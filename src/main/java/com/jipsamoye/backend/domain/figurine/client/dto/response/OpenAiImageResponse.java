package com.jipsamoye.backend.domain.figurine.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiImageResponse(List<ImageData> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageData(@JsonProperty("b64_json") String b64Json) {
    }

    public boolean hasImage() {
        return data != null && !data.isEmpty() && data.get(0).b64Json() != null;
    }

    public String firstB64Json() {
        return data.get(0).b64Json();
    }
}
