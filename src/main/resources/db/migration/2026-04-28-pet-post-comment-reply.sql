-- PR 1: PetPost 대댓글 도입 마이그레이션
-- 실행 순서: DB 변경 → 백엔드 배포 → 프론트 배포

-- ① comments 테이블 컬럼 추가
ALTER TABLE comments
    ADD COLUMN parent_id BIGINT NULL,
    ADD COLUMN mentioned_user_id BIGINT NULL,
    ADD COLUMN is_masked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id),
    ADD CONSTRAINT fk_comments_mentioned_user FOREIGN KEY (mentioned_user_id) REFERENCES users(id);

-- ② 인덱스 추가
ALTER TABLE comments
    ADD INDEX idx_comments_post_parent_created (pet_post_id, parent_id, created_at),
    ADD INDEX idx_comments_parent_created (parent_id, created_at);

-- ③ pet_post 테이블 comment_count 컬럼 추가
ALTER TABLE pet_post
    ADD COLUMN comment_count INT NOT NULL DEFAULT 0;

-- ④ 백필: 기존 댓글 수 집계
UPDATE pet_post p
SET comment_count = (
    SELECT COUNT(*)
    FROM comments c
    WHERE c.pet_post_id = p.id
      AND c.deleted_at IS NULL
);

-- ⑤ notifications 테이블에 related_post_id 추가
-- 알림 클릭 시 게시글 딥링크용. dedup 키(target_id)와 분리.
ALTER TABLE notifications
    ADD COLUMN related_post_id BIGINT NULL;
