package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.dto.request.FigurineJobCreateRequest;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurineJobResponse;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurinePublishResponse;

public interface FigurineService {

    FigurineJobResponse createJob(FigurineJobCreateRequest request, Long userId);

    FigurineJobResponse getJob(Long jobId, Long userId);

    FigurinePublishResponse publishJob(Long jobId, Long userId);
}
