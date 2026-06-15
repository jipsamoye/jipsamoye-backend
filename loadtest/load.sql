-- pet_post 1000만 건 적재 (LOAD DATA LOCAL INFILE)
-- users -> pet_post 순서로 적재해 FK(user_id -> users.id)를 만족시킨다.
--
-- TSV 포맷 약속(DummyDataGenerator/TsvWriter 와 일치):
--   FIELDS TERMINATED BY '\t' ESCAPED BY '\\'  (ENCLOSED BY 미사용)
--   LINES TERMINATED BY '\n'
--   NULL = \N
--
-- 컬럼 순서:
--   users.tsv    : nickname, email, provider, provider_id, role, created_at, updated_at
--   pet_post.tsv : user_id, title, content, image_urls, like_count, comment_count, created_at, updated_at
--
-- 적용 (mysql 클라이언트에서 --local-infile=1 필요):
--   mysql --local-infile=1 -h127.0.0.1 -P3307 -uroot -ploadtest jipsamoye_loadtest < loadtest/load.sql
-- (경로는 환경에 따라 절대경로로 바꿔야 할 수 있다. load.sh 사용 권장.)

LOAD DATA LOCAL INFILE 'loadtest/data/users.tsv'
INTO TABLE users
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t' ESCAPED BY '\\'
LINES TERMINATED BY '\n'
(nickname, email, provider, provider_id, role, created_at, updated_at);

LOAD DATA LOCAL INFILE 'loadtest/data/pet_post.tsv'
INTO TABLE pet_post
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t' ESCAPED BY '\\'
LINES TERMINATED BY '\n'
(user_id, title, content, image_urls, like_count, comment_count, created_at, updated_at);
