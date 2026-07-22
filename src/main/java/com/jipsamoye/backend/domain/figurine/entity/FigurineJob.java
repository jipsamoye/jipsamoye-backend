package com.jipsamoye.backend.domain.figurine.entity;

import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "figurine_job", indexes = {
        @Index(name = "idx_figurine_job_user", columnList = "user_id, created_at")
})
public class FigurineJob extends BaseEntity {

    private static final int FAIL_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FigurineStatus status = FigurineStatus.PENDING;

    @Column(nullable = false, length = 500)
    private String sourceImageUrl;

    @Column(length = 500)
    private String resultImageUrl;

    @Column(length = 500)
    private String failReason;

    // 자동 게시 후 연결되는 PetPost id — 중복 게시 방지 근거
    private Long petPostId;

    @Builder
    public FigurineJob(User user, String sourceImageUrl) {
        this.user = user;
        this.sourceImageUrl = sourceImageUrl;
        this.status = FigurineStatus.PENDING;
    }

    public void startProcessing() {
        this.status = FigurineStatus.PROCESSING;
    }

    public void complete(String resultImageUrl) {
        this.status = FigurineStatus.COMPLETED;
        this.resultImageUrl = resultImageUrl;
        this.failReason = null;
    }

    public void fail(String reason) {
        this.status = FigurineStatus.FAILED;
        this.failReason = truncate(reason);
    }

    public void linkPetPost(Long petPostId) {
        if (this.status != FigurineStatus.COMPLETED) {
            throw new IllegalStateException("완료되지 않은 작업은 게시할 수 없습니다.");
        }
        if (this.petPostId != null) {
            throw new IllegalStateException("이미 게시된 작업입니다.");
        }
        this.petPostId = petPostId;
    }

    public boolean isInProgress() {
        return status == FigurineStatus.PENDING || status == FigurineStatus.PROCESSING;
    }

    private String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() > FAIL_REASON_MAX_LENGTH ? reason.substring(0, FAIL_REASON_MAX_LENGTH) : reason;
    }
}
