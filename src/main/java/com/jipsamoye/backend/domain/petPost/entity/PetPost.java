package com.jipsamoye.backend.domain.petPost.entity;

import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// title FULLTEXT 인덱스(ft_title, WITH PARSER ngram)는 JPA로 표현할 수 없어
// 마이그레이션(2026-06-14-petpost-title-fulltext.sql)으로만 관리한다. ddl-auto:update는 이 인덱스를 건드리지 않는다.
@Table(name = "pet_post", indexes = {
        @Index(name = "idx_pet_post_created_like", columnList = "created_at, like_count")
})
@SQLRestriction("deleted_at IS NULL")
public class PetPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> imageUrls = new ArrayList<>();

    @Column(nullable = false)
    private int likeCount = 0;

    @Column(nullable = false)
    private int commentCount = 0;

    // AI 자동 생성 게시글 여부 (figurine 자동 게시). 생성 시점에만 정해지며 수정되지 않는다.
    @Column(nullable = false)
    private boolean aiGenerated = false;

    @Builder
    public PetPost(User user, String title, String content, List<String> imageUrls, boolean aiGenerated) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.aiGenerated = aiGenerated;
    }

    public void update(String title, String content, List<String> imageUrls) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (imageUrls != null) this.imageUrls = imageUrls;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
