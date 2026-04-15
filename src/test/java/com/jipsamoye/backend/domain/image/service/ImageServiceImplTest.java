package com.jipsamoye.backend.domain.image.service;

import com.jipsamoye.backend.domain.image.dto.request.PresignedUrlRequest;
import com.jipsamoye.backend.domain.image.dto.response.PresignedUrlResponse;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceImplTest {

    @InjectMocks
    private ImageServiceImpl imageService;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private ImageCdnConverter imageCdnConverter;

    private static final String BUCKET = "jipsamoye-bucket";
    private static final String REGION = "ap-northeast-2";
    private static final String CDN_BASE_URL = "https://images.jipsamoye.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(imageService, "bucket", BUCKET);
        ReflectionTestUtils.setField(imageService, "region", REGION);
    }

    @Test
    @DisplayName("generatePresignedUrl은 CDN imageUrl을 반환한다")
    void generatePresignedUrl_returnsCdnImageUrl() throws MalformedURLException {
        PresignedUrlRequest request = mock(PresignedUrlRequest.class);
        when(request.getExt()).thenReturn("jpg");
        when(request.getDirName()).thenReturn("posts");

        PresignedPutObjectRequest presignedPutObjectRequest = mock(PresignedPutObjectRequest.class);
        when(presignedPutObjectRequest.url()).thenReturn(new URL("https://presigned.example.com/upload"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPutObjectRequest);

        when(imageCdnConverter.toCdnUrl(any(String.class))).thenAnswer(invocation -> {
            String s3Url = invocation.getArgument(0);
            String s3Prefix = "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/";
            if (s3Url.startsWith(s3Prefix)) {
                return CDN_BASE_URL + "/" + s3Url.substring(s3Prefix.length());
            }
            return s3Url;
        });

        PresignedUrlResponse response = imageService.generatePresignedUrl(request, 1L);

        assertThat(response.getImageUrl()).startsWith(CDN_BASE_URL);
        assertThat(response.getPresignedUrl()).isNotBlank();
    }
}
