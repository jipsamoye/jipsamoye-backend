package com.jipsamoye.backend.domain.figurine.storage;

import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FigurineImageStorageTest {

    private static final String CDN = "https://images.jipsamoye.com";
    private static final String SOURCE_URL = CDN + "/posts/42/abc-uuid.jpg";

    @Mock
    private S3Client s3Client;

    private FigurineImageStorage storage;

    @BeforeEach
    void setUp() {
        // 재시도 지연 0ms로 주입 (테스트 전용 생성자)
        storage = new FigurineImageStorage(s3Client, "test-bucket", "ap-northeast-2", CDN, 0L);
    }

    private ResponseBytes<GetObjectResponse> bytesOf(String content) {
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("800px 썸네일이 존재하면 webp 소스로 반환한다")
    void downloadSource_thumbnailExists() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(bytesOf("thumb"));

        FigurineSourceImage source = storage.downloadSource(SOURCE_URL);

        assertThat(source.contentType()).isEqualTo("image/webp");
        assertThat(source.filename()).isEqualTo("source.webp");
        assertThat(source.bytes()).isEqualTo("thumb".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("posts/42/thumbnails/abc-uuid_800.webp");
    }

    @Test
    @DisplayName("썸네일이 늦게 생성되면 재시도 후 성공한다")
    void downloadSource_thumbnailAppearsAfterRetry() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenReturn(bytesOf("thumb"));

        FigurineSourceImage source = storage.downloadSource(SOURCE_URL);

        assertThat(source.contentType()).isEqualTo("image/webp");
        verify(s3Client, times(3)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("썸네일이 5회 모두 없으면 원본으로 폴백한다")
    void downloadSource_fallsBackToOriginal() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(NoSuchKeyException.builder().build())
                .thenReturn(bytesOf("original"));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(1024L).contentType("image/jpeg").build());

        FigurineSourceImage source = storage.downloadSource(SOURCE_URL);

        assertThat(source.contentType()).isEqualTo("image/jpeg");
        assertThat(source.filename()).isEqualTo("abc-uuid.jpg");
        assertThat(source.bytes()).isEqualTo("original".getBytes(StandardCharsets.UTF_8));
        verify(s3Client, times(6)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("원본이 10MB를 초과하면 FIGURINE_GENERATION_FAILED 예외를 던진다")
    void downloadSource_originalTooLarge_throws() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(11L * 1024 * 1024).contentType("image/jpeg").build());

        assertThatThrownBy(() -> storage.downloadSource(SOURCE_URL))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_GENERATION_FAILED));
    }

    @Test
    @DisplayName("우리 CDN/S3 URL이 아니면 BAD_REQUEST 예외를 던진다")
    void downloadSource_foreignUrl_throws() {
        assertThatThrownBy(() -> storage.downloadSource("https://evil.example.com/cat.jpg"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("uploadResult는 posts/{userId}/{uuid}.png로 업로드하고 CDN URL을 반환한다")
    void uploadResult_uploadsAndReturnsCdnUrl() {
        byte[] png = "png".getBytes(StandardCharsets.UTF_8);

        String url = storage.uploadResult(42L, png);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).matches("posts/42/[0-9a-f-]{36}\\.png");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo(CDN + "/" + captor.getValue().key());
    }
}
