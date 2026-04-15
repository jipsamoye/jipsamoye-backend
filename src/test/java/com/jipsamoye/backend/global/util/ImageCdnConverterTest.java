package com.jipsamoye.backend.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCdnConverterTest {

    private final ImageCdnConverter converter = new ImageCdnConverter(
            "https://images.jipsamoye.com",
            "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com"
    );

    @Test
    @DisplayName("S3 URL을 CDN URL로 변환한다")
    void toCdnUrl_convertsS3Url() {
        String s3Url = "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/uuid.jpg";
        String result = converter.toCdnUrl(s3Url);
        assertThat(result).isEqualTo("https://images.jipsamoye.com/posts/1/uuid.jpg");
    }

    @Test
    @DisplayName("이미 CDN URL이면 그대로 반환한다")
    void toCdnUrl_alreadyCdnUrl() {
        String cdnUrl = "https://images.jipsamoye.com/posts/1/uuid.jpg";
        String result = converter.toCdnUrl(cdnUrl);
        assertThat(result).isEqualTo("https://images.jipsamoye.com/posts/1/uuid.jpg");
    }

    @Test
    @DisplayName("null이면 null을 반환한다")
    void toCdnUrl_null() {
        assertThat(converter.toCdnUrl(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열이면 빈 문자열을 반환한다")
    void toCdnUrl_empty() {
        assertThat(converter.toCdnUrl("")).isEmpty();
    }

    @Test
    @DisplayName("URL 리스트를 CDN URL 리스트로 변환한다")
    void toCdnUrls_convertsList() {
        List<String> urls = List.of(
                "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/a.jpg",
                "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/b.webp"
        );
        List<String> result = converter.toCdnUrls(urls);
        assertThat(result).containsExactly(
                "https://images.jipsamoye.com/posts/1/a.jpg",
                "https://images.jipsamoye.com/posts/1/b.webp"
        );
    }

    @Test
    @DisplayName("null 리스트이면 빈 리스트를 반환한다")
    void toCdnUrls_nullList() {
        assertThat(converter.toCdnUrls(null)).isEmpty();
    }

    @Test
    @DisplayName("CDN URL에서 S3 key를 추출한다")
    void extractKey_fromCdnUrl() {
        String cdnUrl = "https://images.jipsamoye.com/posts/1/uuid.jpg";
        String key = converter.extractKey(cdnUrl);
        assertThat(key).isEqualTo("posts/1/uuid.jpg");
    }

    @Test
    @DisplayName("S3 URL에서 S3 key를 추출한다")
    void extractKey_fromS3Url() {
        String s3Url = "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/uuid.jpg";
        String key = converter.extractKey(s3Url);
        assertThat(key).isEqualTo("posts/1/uuid.jpg");
    }

    @Test
    @DisplayName("null URL에서 key 추출 시 null을 반환한다")
    void extractKey_null() {
        assertThat(converter.extractKey(null)).isNull();
    }
}
