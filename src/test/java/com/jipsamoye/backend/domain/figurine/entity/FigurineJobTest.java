package com.jipsamoye.backend.domain.figurine.entity;

import com.jipsamoye.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigurineJobTest {

    private FigurineJob newJob() {
        return FigurineJob.builder()
                .user(Mockito.mock(User.class))
                .sourceImageUrl("https://images.jipsamoye.com/posts/1/abc.webp")
                .build();
    }

    @Test
    @DisplayName("생성 직후 상태는 PENDING이다")
    void initialStatus_isPending() {
        FigurineJob job = newJob();
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.PENDING);
        assertThat(job.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("startProcessing 호출 시 PROCESSING으로 전이한다")
    void startProcessing_transitionsToProcessing() {
        FigurineJob job = newJob();
        job.startProcessing();
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.PROCESSING);
        assertThat(job.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("complete 호출 시 COMPLETED로 전이하고 결과 URL을 저장한다")
    void complete_storesResultUrl() {
        FigurineJob job = newJob();
        job.startProcessing();
        job.complete("https://images.jipsamoye.com/posts/1/result.png");
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.COMPLETED);
        assertThat(job.getResultImageUrl()).isEqualTo("https://images.jipsamoye.com/posts/1/result.png");
        assertThat(job.getFailReason()).isNull();
        assertThat(job.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("fail 호출 시 FAILED로 전이하고 사유를 저장한다")
    void fail_storesReason() {
        FigurineJob job = newJob();
        job.startProcessing();
        job.fail("OpenAI API 호출 실패");
        assertThat(job.getStatus()).isEqualTo(FigurineStatus.FAILED);
        assertThat(job.getFailReason()).isEqualTo("OpenAI API 호출 실패");
        assertThat(job.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("fail 사유가 500자를 넘으면 잘라서 저장한다")
    void fail_truncatesLongReason() {
        FigurineJob job = newJob();
        job.fail("가".repeat(600));
        assertThat(job.getFailReason()).hasSize(500);
    }

    @Test
    @DisplayName("COMPLETED 상태에서 linkPetPost가 petPostId를 연결한다")
    void linkPetPost_onCompleted_succeeds() {
        FigurineJob job = newJob();
        job.complete("https://images.jipsamoye.com/posts/1/result.png");
        job.linkPetPost(77L);
        assertThat(job.getPetPostId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("미완료 상태에서 linkPetPost는 IllegalStateException을 던진다")
    void linkPetPost_notCompleted_throws() {
        FigurineJob job = newJob();
        assertThatThrownBy(() -> job.linkPetPost(77L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 게시된 작업에 linkPetPost를 다시 호출하면 IllegalStateException을 던진다")
    void linkPetPost_alreadyLinked_throws() {
        FigurineJob job = newJob();
        job.complete("https://images.jipsamoye.com/posts/1/result.png");
        job.linkPetPost(77L);
        assertThatThrownBy(() -> job.linkPetPost(88L))
                .isInstanceOf(IllegalStateException.class);
    }
}
