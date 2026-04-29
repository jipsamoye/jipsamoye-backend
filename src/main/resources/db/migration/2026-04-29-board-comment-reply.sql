-- PR 2: Board(자유게시판) 대댓글 도입 마이그레이션
-- 실행 순서: DB 변경 → 백엔드 배포 → 프론트 배포
-- Board.comment_count 컬럼은 이미 존재 — 백필 불필요

-- ① board_comments 테이블 컬럼 추가
ALTER TABLE board_comments
    ADD COLUMN parent_id BIGINT NULL,
    ADD COLUMN mentioned_user_id BIGINT NULL,
    ADD COLUMN is_masked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_board_comments_parent FOREIGN KEY (parent_id) REFERENCES board_comments(id),
    ADD CONSTRAINT fk_board_comments_mentioned_user FOREIGN KEY (mentioned_user_id) REFERENCES users(id);

-- ② 인덱스 추가
ALTER TABLE board_comments
    ADD INDEX idx_board_comments_board_parent_created (board_id, parent_id, created_at),
    ADD INDEX idx_board_comments_parent_created (parent_id, created_at);
