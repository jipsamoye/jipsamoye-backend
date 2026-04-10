package com.jipsamoye.backend.domain.user.entity;

import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String nickname;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String profileImageUrl;

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
    public User(String nickname, String bio, String profileImageUrl,
                String email, Provider provider, String providerId, Role role) {
        this.nickname = nickname;
        this.bio = bio;
        this.profileImageUrl = profileImageUrl;
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
    }

    public void updateProfile(String nickname, String bio, String profileImageUrl) {
        if (nickname != null) this.nickname = nickname;
        if (bio != null) this.bio = bio;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
    }
}
