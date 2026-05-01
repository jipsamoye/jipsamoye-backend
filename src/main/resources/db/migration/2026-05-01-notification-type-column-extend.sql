-- notifications.type 컬럼 길이 확장
-- 원인: NotificationType enum에 PET_POST_COMMENT_REPLY(22자), BOARD_COMMENT_REPLY(19자)가
--       추가되었으나 운영 DB 컬럼이 그보다 짧아 INSERT 시 'Data truncated for column type' 에러 발생
-- 실행 순서: DB 변경 → 백엔드 재배포 (엔티티 length 명시는 호환되므로 순서 영향 적음)

ALTER TABLE notifications
    MODIFY COLUMN type VARCHAR(50) NOT NULL;
