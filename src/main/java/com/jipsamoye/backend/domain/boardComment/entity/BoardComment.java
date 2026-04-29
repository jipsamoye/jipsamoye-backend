package com.jipsamoye.backend.domain.boardComment.entity;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "board_comments", indexes = {
        @Index(name = "idx_board_comments_board_parent_created", columnList = "board_id, parent_id, created_at"),
        @Index(name = "idx_board_comments_parent_created", columnList = "parent_id, created_at")
})
@SQLRestriction("deleted_at IS NULL")
public class BoardComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT", length = 500)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private BoardComment parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentioned_user_id")
    private User mentionedUser;

    @Column(nullable = false)
    private boolean isMasked = false;

    @Builder
    public BoardComment(Board board, User user, String content, BoardComment parent, User mentionedUser) {
        this.board = board;
        this.user = user;
        this.content = content;
        this.parent = parent;
        this.mentionedUser = mentionedUser;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void mask() {
        this.isMasked = true;
    }

    public boolean isReply() {
        return parent != null;
    }
}
