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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FigurineServiceImplTest {

    private static final String CDN = "https://images.jipsamoye.com";
    private static final String SOURCE_URL = CDN + "/posts/42/abc.jpg";
    private static final String RESULT_URL = CDN + "/posts/42/result.png";

    @InjectMocks
    private FigurineServiceImpl figurineService;

    @Mock
    private FigurineJobRepository figurineJobRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FigurineJobProcessor figurineJobProcessor;
    @Mock
    private PetPostService petPostService;
    @Mock
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(figurineService, "cdnBaseUrl", CDN);
    }

    private FigurineJob ownedJob(Long jobId, Long ownerId) {
        when(user.getId()).thenReturn(ownerId);
        FigurineJob job = FigurineJob.builder().user(user).sourceImageUrl(SOURCE_URL).build();
        ReflectionTestUtils.setField(job, "id", jobId);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.now());
        when(figurineJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        return job;
    }

    private FigurineJob ownedLockedJob(Long jobId, Long ownerId) {
        when(user.getId()).thenReturn(ownerId);
        FigurineJob job = FigurineJob.builder().user(user).sourceImageUrl(SOURCE_URL).build();
        ReflectionTestUtils.setField(job, "id", jobId);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.now());
        when(figurineJobRepository.findWithLockById(jobId)).thenReturn(Optional.of(job));
        return job;
    }

    @Test
    @DisplayName("createJob은 job을 저장하고 비동기 프로세서를 호출한 뒤 PENDING을 반환한다")
    void createJob_savesAndTriggersProcessor() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(figurineJobRepository.save(any(FigurineJob.class))).thenAnswer(invocation -> {
            FigurineJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 1L);
            return job;
        });

        FigurineJobResponse response = figurineService.createJob(new FigurineJobCreateRequest(SOURCE_URL), 42L);

        assertThat(response.jobId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(FigurineStatus.PENDING);
        verify(figurineJobProcessor).process(1L, 42L);
    }

    @Test
    @DisplayName("createJob은 우리 CDN URL이 아니면 BAD_REQUEST를 던진다")
    void createJob_foreignUrl_throws() {
        assertThatThrownBy(() -> figurineService.createJob(
                new FigurineJobCreateRequest("https://evil.example.com/cat.jpg"), 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verify(figurineJobProcessor, never()).process(any(), any());
    }

    @Test
    @DisplayName("getJob은 본인 소유가 아니면 FORBIDDEN을 던진다")
    void getJob_notOwner_throws() {
        ownedJob(1L, 42L);

        assertThatThrownBy(() -> figurineService.getJob(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("getJob은 존재하지 않는 job이면 FIGURINE_JOB_NOT_FOUND를 던진다")
    void getJob_notFound_throws() {
        when(figurineJobRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> figurineService.getJob(1L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_JOB_NOT_FOUND));
    }

    @Test
    @DisplayName("getJob은 5분 넘게 진행 중인 job을 FAILED로 전환한다")
    void getJob_staleInProgress_marksFailed() {
        FigurineJob job = ownedJob(1L, 42L);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.now().minusMinutes(6));

        FigurineJobResponse response = figurineService.getJob(1L, 42L);

        assertThat(response.status()).isEqualTo(FigurineStatus.FAILED);
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.FAILED);
    }

    @Test
    @DisplayName("getJob은 5분 이내 진행 중인 job을 그대로 반환한다")
    void getJob_freshInProgress_returnsAsIs() {
        ownedJob(1L, 42L);

        FigurineJobResponse response = figurineService.getJob(1L, 42L);

        assertThat(response.status()).isEqualTo(FigurineStatus.PENDING);
    }

    @Test
    @DisplayName("publishJob은 완료된 job으로 petPost를 만들고 petPostId를 연결한다")
    void publishJob_success() {
        FigurineJob job = ownedLockedJob(1L, 42L);
        job.complete(RESULT_URL);
        PetPostResponse post = new PetPostResponse(77L, "AI 키캡 자랑", null, List.of(RESULT_URL),
                0, 0, "집사", null, LocalDateTime.now(), LocalDateTime.now());
        when(petPostService.createPost(any(PetPostCreateRequest.class), any(Long.class))).thenReturn(post);

        FigurinePublishResponse response = figurineService.publishJob(1L, 42L);

        assertThat(response.petPostId()).isEqualTo(77L);
        assertThat(job.getPetPostId()).isEqualTo(77L);
        ArgumentCaptor<PetPostCreateRequest> captor = ArgumentCaptor.forClass(PetPostCreateRequest.class);
        verify(petPostService).createPost(captor.capture(), any(Long.class));
        assertThat(captor.getValue().title()).isEqualTo("AI 키캡 자랑");
        assertThat(captor.getValue().imageUrls()).containsExactly(RESULT_URL);
    }

    @Test
    @DisplayName("publishJob은 미완료 job이면 FIGURINE_JOB_NOT_COMPLETED를 던진다")
    void publishJob_notCompleted_throws() {
        ownedLockedJob(1L, 42L);

        assertThatThrownBy(() -> figurineService.publishJob(1L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_JOB_NOT_COMPLETED));
    }

    @Test
    @DisplayName("publishJob은 이미 게시된 job이면 FIGURINE_ALREADY_POSTED를 던진다")
    void publishJob_alreadyPosted_throws() {
        FigurineJob job = ownedLockedJob(1L, 42L);
        job.complete(RESULT_URL);
        job.linkPetPost(77L);

        assertThatThrownBy(() -> figurineService.publishJob(1L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_ALREADY_POSTED));
    }

    @Test
    @DisplayName("publishJob은 존재하지 않는 job이면 FIGURINE_JOB_NOT_FOUND를 던진다")
    void publishJob_notFound_throws() {
        when(figurineJobRepository.findWithLockById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> figurineService.publishJob(1L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FIGURINE_JOB_NOT_FOUND));
    }

    @Test
    @DisplayName("createJob은 본인 posts 경로가 아닌 이미지면 BAD_REQUEST를 던지고 프로세서를 호출하지 않는다")
    void createJob_notOwnPath_throws() {
        assertThatThrownBy(() -> figurineService.createJob(
                new FigurineJobCreateRequest(CDN + "/posts/99/abc.jpg"), 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verify(figurineJobProcessor, never()).process(any(), any());
    }
}
