package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.client.FigurineImageClient;
import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.repository.FigurineJobRepository;
import com.jipsamoye.backend.domain.figurine.storage.FigurineImageStorage;
import com.jipsamoye.backend.domain.figurine.storage.FigurineSourceImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FigurineJobProcessor {

    private final FigurineJobRepository figurineJobRepository;
    private final FigurineImageStorage figurineImageStorage;
    private final FigurineImageClient figurineImageClient;

    /**
     * S3 다운로드 → OpenAI 변환 → S3 업로드를 비동기 수행한다.
     * 트랜잭션을 열지 않는다 — 외부 I/O(최대 2분)가 커넥션을 점유하지 않도록 save 단위로만 커밋.
     *
     * @param userId LAZY user를 트랜잭션 밖에서 터치하지 않기 위해 별도 전달
     */
    @Async("figurineExecutor")
    public void process(Long jobId, Long userId) {
        FigurineJob job = figurineJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("figurine job을 찾을 수 없습니다: jobId={}", jobId);
            return;
        }
        job.startProcessing();
        figurineJobRepository.save(job);
        try {
            FigurineSourceImage source = figurineImageStorage.downloadSource(job.getSourceImageUrl());
            byte[] resultPng = figurineImageClient.generateKeycapImage(
                    source.bytes(), source.contentType(), source.filename());
            String resultImageUrl = figurineImageStorage.uploadResult(userId, resultPng);
            job.complete(resultImageUrl);
        } catch (Exception e) {
            log.error("figurine 생성 실패: jobId={}", jobId, e);
            job.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        figurineJobRepository.save(job);
    }
}
