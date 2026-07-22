package com.jipsamoye.backend.domain.figurine.service;

import com.jipsamoye.backend.domain.figurine.dto.request.FigurineJobCreateRequest;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurineJobResponse;
import com.jipsamoye.backend.domain.figurine.dto.response.FigurinePublishResponse;
import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import com.jipsamoye.backend.domain.figurine.entity.FigurineStatus;
import com.jipsamoye.backend.domain.figurine.repository.FigurineJobRepository;
import com.jipsamoye.backend.domain.petPost.dto.request.PetPostCreateRequest;
import com.jipsamoye.backend.domain.petPost.dto.response.PetPostResponse;
import com.jipsamoye.backend.domain.petPost.service.PetPostService;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FigurineServiceImpl implements FigurineService {

    private static final Duration JOB_TIMEOUT = Duration.ofMinutes(5);
    private static final String AUTO_POST_TITLE = "AI 키캡 자랑";

    private final FigurineJobRepository figurineJobRepository;
    private final UserRepository userRepository;
    private final FigurineJobProcessor figurineJobProcessor;
    private final PetPostService petPostService;

    @Value("${cdn.image-base-url:https://images.jipsamoye.com}")
    private String cdnBaseUrl;

    /**
     * 의도적으로 @Transactional 없음 — save가 자체 트랜잭션으로 먼저 커밋된 뒤
     * 비동기 프로세서를 호출해야 프로세서 스레드가 커밋 전 job을 못 찾는 레이스가 없다.
     */
    @Override
    public FigurineJobResponse createJob(FigurineJobCreateRequest request, Long userId) {
        if (!request.sourceImageUrl().startsWith(cdnBaseUrl + "/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "지원하지 않는 이미지 URL입니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        FigurineJob job = figurineJobRepository.save(FigurineJob.builder()
                .user(user)
                .sourceImageUrl(request.sourceImageUrl())
                .build());
        figurineJobProcessor.process(job.getId(), userId);
        return FigurineJobResponse.from(job);
    }

    @Override
    @Transactional
    public FigurineJobResponse getJob(Long jobId, Long userId) {
        FigurineJob job = findOwnedJob(jobId, userId);
        // 서버 재시작 등으로 유실된 job 방어: 5분 초과 진행 중이면 FAILED 전환 (더티체킹으로 저장)
        if (job.isInProgress() && job.getCreatedAt().isBefore(LocalDateTime.now().minus(JOB_TIMEOUT))) {
            job.fail("처리 시간이 5분을 초과했습니다. 다시 시도해주세요.");
        }
        return FigurineJobResponse.from(job);
    }

    @Override
    @Transactional
    public FigurinePublishResponse publishJob(Long jobId, Long userId) {
        FigurineJob job = findOwnedJob(jobId, userId);
        if (job.getPetPostId() != null) {
            throw new BusinessException(ErrorCode.FIGURINE_ALREADY_POSTED);
        }
        if (job.getStatus() != FigurineStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.FIGURINE_JOB_NOT_COMPLETED);
        }
        PetPostResponse post = petPostService.createPost(
                new PetPostCreateRequest(AUTO_POST_TITLE, null, List.of(job.getResultImageUrl())), userId);
        job.linkPetPost(post.id());
        return new FigurinePublishResponse(post.id());
    }

    private FigurineJob findOwnedJob(Long jobId, Long userId) {
        FigurineJob job = figurineJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FIGURINE_JOB_NOT_FOUND));
        if (!job.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return job;
    }
}
