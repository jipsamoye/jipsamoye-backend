package com.jipsamoye.backend.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "cdn")
public class CdnProperties {

    private final String imageBaseUrl;
}
