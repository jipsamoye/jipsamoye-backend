package com.jipsamoye.backend.domain.like.entity;

import com.jipsamoye.backend.domain.petPost.entity.PetPost;
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
@Table(name = "likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"pet_post_id", "user_id"})
})
public class Like extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_post_id", nullable = false)
    private PetPost petPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    public Like(PetPost petPost, User user) {
        this.petPost = petPost;
        this.user = user;
    }
}
