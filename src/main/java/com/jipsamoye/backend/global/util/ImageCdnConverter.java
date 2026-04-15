package com.jipsamoye.backend.global.util;

import com.jipsamoye.backend.global.config.CdnProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ImageCdnConverter {

    private final String cdnBaseUrl;
    private final String s3BaseUrl;

    public ImageCdnConverter(
            CdnProperties cdnProperties,
            @Value("${cloud.aws.s3.bucket}") String bucket
    ) {
        this.cdnBaseUrl = cdnProperties.getImageBaseUrl();
        this.s3BaseUrl = "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com";
    }

    // 테스트용 생성자
    ImageCdnConverter(String cdnBaseUrl, String s3BaseUrl) {
        this.cdnBaseUrl = cdnBaseUrl;
        this.s3BaseUrl = s3BaseUrl;
    }

    public String toCdnUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;
        if (imageUrl.startsWith(s3BaseUrl)) {
            return cdnBaseUrl + imageUrl.substring(s3BaseUrl.length());
        }
        return imageUrl;
    }

    public List<String> toCdnUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return Collections.emptyList();
        return imageUrls.stream().map(this::toCdnUrl).toList();
    }

    public String extractKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        if (imageUrl.startsWith(cdnBaseUrl)) {
            return imageUrl.substring(cdnBaseUrl.length() + 1);
        }
        if (imageUrl.startsWith(s3BaseUrl)) {
            return imageUrl.substring(s3BaseUrl.length() + 1);
        }
        return null;
    }
}
