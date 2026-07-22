package com.jipsamoye.backend.domain.figurine.dto.response;

import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.entity.FigurineStatus;

public record FigurineJobResponse(
        Long jobId,
        FigurineStatus status,
        String resultImageUrl,
        String failReason,
        Long petPostId
) {
    public static FigurineJobResponse from(FigurineJob job) {
        return new FigurineJobResponse(
                job.getId(),
                job.getStatus(),
                job.getResultImageUrl(),
                job.getFailReason(),
                job.getPetPostId()
        );
    }
}
