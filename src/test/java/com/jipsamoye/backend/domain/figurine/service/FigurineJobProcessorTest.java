package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.client.FigurineImageClient;
import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.entity.FigurineStatus;
import com.jipsamoye.backend.domain.figurine.repository.FigurineJobRepository;
import com.jipsamoye.backend.domain.figurine.storage.FigurineImageStorage;
import com.jipsamoye.backend.domain.figurine.storage.FigurineSourceImage;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FigurineJobProcessorTest {

    private static final String SOURCE_URL = "https://images.jipsamoye.com/posts/42/abc.jpg";
    private static final String RESULT_URL = "https://images.jipsamoye.com/posts/42/result.png";

    @InjectMocks
    private FigurineJobProcessor processor;

    @Mock
    private FigurineJobRepository figurineJobRepository;
    @Mock
    private FigurineImageStorage figurineImageStorage;
    @Mock
    private FigurineImageClient figurineImageClient;

    private FigurineJob newJob() {
        return FigurineJob.builder()
                .user(Mockito.mock(User.class))
                .sourceImageUrl(SOURCE_URL)
                .build();
    }

    @Test
    @DisplayName("성공 시 job이 COMPLETED로 저장되고 결과 URL이 연결된다")
    void process_success_completesJob() {
        FigurineJob job = newJob();
        byte[] source = "source".getBytes(StandardCharsets.UTF_8);
        byte[] result = "result-png".getBytes(StandardCharsets.UTF_8);
        when(figurineJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(figurineImageStorage.downloadSource(SOURCE_URL))
                .thenReturn(new FigurineSourceImage(source, "image/webp", "source.webp"));
        when(figurineImageClient.generateKeycapImage(source, "image/webp", "source.webp")).thenReturn(result);
        when(figurineImageStorage.uploadResult(42L, result)).thenReturn(RESULT_URL);

        processor.process(1L, 42L);

        assertThat(job.getStatus()).isEqualTo(FigurineStatus.COMPLETED);
        assertThat(job.getResultImageUrl()).isEqualTo(RESULT_URL);
        verify(figurineJobRepository, times(2)).save(job);
    }

    @Test
    @DisplayName("OpenAI 호출이 실패하면 job이 FAILED로 저장되고 사유가 남는다")
    void process_clientFails_marksFailed() {
        FigurineJob job = newJob();
        when(figurineJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(figurineImageStorage.downloadSource(SOURCE_URL))
                .thenReturn(new FigurineSourceImage("s".getBytes(StandardCharsets.UTF_8), "image/webp", "source.webp"));
        when(figurineImageClient.generateKeycapImage(any(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "OpenAI API 호출 실패"));

        processor.process(1L, 42L);

        assertThat(job.getStatus()).isEqualTo(FigurineStatus.FAILED);
        assertThat(job.getFailReason()).contains("OpenAI API 호출 실패");
        verify(figurineJobRepository, times(2)).save(job);
    }

    @Test
    @DisplayName("S3 다운로드가 실패해도 job이 FAILED로 저장된다")
    void process_downloadFails_marksFailed() {
        FigurineJob job = newJob();
        when(figurineJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(figurineImageStorage.downloadSource(SOURCE_URL))
                .thenThrow(new BusinessException(ErrorCode.FIGURINE_GENERATION_FAILED, "원본 이미지를 찾을 수 없습니다"));

        processor.process(1L, 42L);

        assertThat(job.getStatus()).isEqualTo(FigurineStatus.FAILED);
        assertThat(job.getFailReason()).contains("원본 이미지를 찾을 수 없습니다");
        verifyNoInteractions(figurineImageClient);
    }

    @Test
    @DisplayName("job이 존재하지 않으면 아무 작업도 하지 않는다")
    void process_jobNotFound_returns() {
        when(figurineJobRepository.findById(anyLong())).thenReturn(Optional.empty());

        processor.process(999L, 42L);

        verifyNoInteractions(figurineImageStorage, figurineImageClient);
    }
}
