package com.jipsamoye.backend.domain.user.entity;

import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime deletedAt;

    @Column(nullable = false, unique = true, length = 10)
    private String nickname;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String profileImageUrl;

    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<SocialLink> socialLinks = new ArrayList<>();

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder
    public User(String nickname, String bio, String profileImageUrl, String coverImageUrl,
                List<SocialLink> socialLinks, String email, Provider provider, String providerId, Role role) {
        this.nickname = nickname;
        this.bio = bio;
        this.profileImageUrl = profileImageUrl;
        this.coverImageUrl = coverImageUrl;
        this.socialLinks = socialLinks != null ? socialLinks : new ArrayList<>();
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
    }

    public void updateProfile(String nickname, String bio, String profileImageUrl,
                              String coverImageUrl, List<SocialLink> socialLinks) {
        if (nickname != null) this.nickname = nickname;
        if (bio != null) this.bio = bio;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
        if (coverImageUrl != null) this.coverImageUrl = coverImageUrl;
        if (socialLinks != null) this.socialLinks = socialLinks;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
