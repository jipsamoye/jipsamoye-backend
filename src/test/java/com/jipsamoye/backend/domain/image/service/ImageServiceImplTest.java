package com.jipsamoye.backend.domain.image.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ImageServiceImplTest {

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private ImageServiceImpl imageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);
        imageService = new ImageServiceImpl(s3Presigner, s3Client);

        // @Value 필드 수동 주입
        ReflectionTestUtils.setField(imageService, "bucket", "jipsamoye-bucket");
        ReflectionTestUtils.setField(imageService, "region", "ap-northeast-2");
        ReflectionTestUtils.setField(imageService, "cdnBaseUrl", "https://images.jipsamoye.com");
    }

    @Test
    @DisplayName("deleteImage - 원본 삭제 시 200/800 썸네일 key도 함께 DeleteObjects 배치에 포함된다")
    void deleteImage_includesThumbnailKeys() {
        String originalUrl = "https://images.jipsamoye.com/posts/1/abc-xyz.webp";

        imageService.deleteImage(originalUrl);

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());

        List<String> keys = captor.getValue().delete().objects().stream()
                .map(obj -> obj.key())
                .toList();

        assertThat(keys).containsExactlyInAnyOrder(
                "posts/1/abc-xyz.webp",
                "posts/1/thumbnails/abc-xyz_200.webp",
                "posts/1/thumbnails/abc-xyz_800.webp"
        );
    }

    @Test
    @DisplayName("deleteImages - 여러 원본을 한 번의 DeleteObjects 호출로 배치 삭제하고 각각 썸네일을 포함한다")
    void deleteImages_batchDeletesAllOriginalsAndThumbnails() {
        List<String> urls = List.of(
                "https://images.jipsamoye.com/posts/1/a.webp",
                "https://images.jipsamoye.com/posts/1/b.jpg"
        );

        imageService.deleteImages(urls);

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());

        List<String> keys = captor.getValue().delete().objects().stream()
                .map(obj -> obj.key())
                .toList();

        // 2 원본 × 3 (원본 + 200 + 800) = 6개
        assertThat(keys).hasSize(6);
        assertThat(keys).containsExactlyInAnyOrder(
                "posts/1/a.webp",
                "posts/1/thumbnails/a_200.webp",
                "posts/1/thumbnails/a_800.webp",
                "posts/1/b.jpg",
                "posts/1/thumbnails/b_200.webp",
                "posts/1/thumbnails/b_800.webp"
        );
    }

    @Test
    @DisplayName("deleteImages - 빈 리스트나 null이면 S3 호출 자체가 발생하지 않는다")
    void deleteImages_skipsWhenEmpty() {
        imageService.deleteImages(null);
        imageService.deleteImages(List.of());

        verify(s3Client, never()).deleteObjects((DeleteObjectsRequest) null);
    }

    @Test
    @DisplayName("deleteImages - CDN prefix 매칭 안 되는 URL은 걸러지고 나머지만 삭제된다")
    void deleteImages_skipsUnrecognizedUrls() {
        List<String> urls = List.of(
                "https://images.jipsamoye.com/posts/1/valid.webp",
                "https://외부도메인.com/something.jpg"
        );

        imageService.deleteImages(urls);

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());

        List<String> keys = captor.getValue().delete().objects().stream()
                .map(obj -> obj.key())
                .toList();

        assertThat(keys).hasSize(3);
        assertThat(keys).allMatch(k -> k.startsWith("posts/1/"));
    }

    @Test
    @DisplayName("deleteImage - S3.amazonaws.com 형식 URL도 CDN URL과 동일하게 처리된다")
    void deleteImage_handlesS3DirectUrlToo() {
        String s3Url = "https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/5/direct.png";

        imageService.deleteImage(s3Url);

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());

        List<String> keys = captor.getValue().delete().objects().stream()
                .map(obj -> obj.key())
                .toList();

        assertThat(keys).containsExactlyInAnyOrder(
                "posts/5/direct.png",
                "posts/5/thumbnails/direct_200.webp",
                "posts/5/thumbnails/direct_800.webp"
        );
    }
}
