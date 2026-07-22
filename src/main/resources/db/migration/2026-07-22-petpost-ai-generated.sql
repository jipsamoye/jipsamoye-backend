-- PetPost에 AI 자동 생성 여부(ai_generated) 컬럼 추가
-- 원인: 프론트가 AI 키캡 게시글에 라벨을 표시할 때 제목("AI 키캡 자랑") 일치 휴리스틱을 쓰고 있어
--       유저가 같은 제목으로 직접 쓴 글에도 라벨이 붙는 오탐이 발생.
-- 실행 순서: DB 변경 → 백엔드 배포
--   (ddl-auto:update도 컬럼은 추가하지만 기존 figurine 게시글 백필은 하지 않으므로 이 SQL을 먼저 실행한다)

ALTER TABLE pet_post
    ADD COLUMN ai_generated BIT(1) NOT NULL DEFAULT b'0';

-- 기존 figurine 자동 게시글 백필 (figurine_job.pet_post_id로 연결된 게시글)
UPDATE pet_post p
    JOIN figurine_job f ON f.pet_post_id = p.id
SET p.ai_generated = b'1';
