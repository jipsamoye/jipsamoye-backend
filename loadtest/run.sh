#!/usr/bin/env bash
# 성능 측정용 pet_post 1000만 건 적재 — end-to-end 재현 스크립트
#
# 절차: 컨테이너 기동 -> DDL 적용 -> CSV(TSV) 생성 -> LOAD DATA -> 검증
# 프로젝트 루트에서 실행한다:  ./loadtest/run.sh
#
# 주의: pet_post.tsv 는 약 3GB, 생성/적재에 수 분 소요된다(환경에 따라 5~15분).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE="docker compose -f docker-compose.loadtest.yml"
DB_CONTAINER="jipsamoye-db-loadtest"
DB="jipsamoye_loadtest"
MYSQL="mysql --local-infile=1 --default-character-set=utf8mb4 -uroot -ploadtest"

echo "==> 1. 측정용 MySQL 컨테이너 기동"
$COMPOSE up -d

echo "==> 2. MySQL 헬스 대기"
until docker exec "$DB_CONTAINER" mysqladmin ping -h localhost -uroot -ploadtest --silent 2>/dev/null; do
  sleep 2
done
echo "    ready."

echo "==> 3. DDL 적용 (users / pet_post)"
docker exec -i "$DB_CONTAINER" $MYSQL "$DB" < loadtest/schema.sql

echo "==> 4. 더미 데이터 생성 (순수 Java -> TSV)"
./gradlew testClasses -q
java -cp build/classes/java/test:build/classes/java/main \
  com.jipsamoye.backend.loadtest.DummyDataGenerator loadtest/data

echo "==> 5. 컨테이너로 TSV 복사 후 LOAD DATA"
docker cp loadtest/data/users.tsv    "$DB_CONTAINER":/tmp/users.tsv
docker cp loadtest/data/pet_post.tsv "$DB_CONTAINER":/tmp/pet_post.tsv

docker exec -i "$DB_CONTAINER" $MYSQL "$DB" <<'SQL'
LOAD DATA LOCAL INFILE '/tmp/users.tsv'
INTO TABLE users CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t' ESCAPED BY '\\' LINES TERMINATED BY '\n'
(nickname, email, provider, provider_id, role, created_at, updated_at);

LOAD DATA LOCAL INFILE '/tmp/pet_post.tsv'
INTO TABLE pet_post CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t' ESCAPED BY '\\' LINES TERMINATED BY '\n'
(user_id, title, content, image_urls, like_count, comment_count, created_at, updated_at);
SQL

echo "==> 6. 검증"
docker exec -i "$DB_CONTAINER" $MYSQL "$DB" <<'SQL'
SELECT COUNT(*) AS user_count FROM users;                                   -- 10,000
SELECT COUNT(*) AS post_count FROM pet_post;                                -- 10,000,000
SELECT COUNT(*) - COUNT(DISTINCT title) AS dup_count,
       ROUND((COUNT(*)-COUNT(DISTINCT title))/COUNT(*)*100,3) AS dup_pct
FROM pet_post;                                                              -- 중복 (목표 < 1%, 실측 ~0.004%)
SELECT COUNT(*) AS cat_count   FROM pet_post WHERE title LIKE '%고양이%';   -- ~800,000 (8%)
SELECT COUNT(*) AS dog_count   FROM pet_post WHERE title LIKE '%강아지%';   -- ~1,200,000 (12%)
SELECT COUNT(*) AS hosp_count  FROM pet_post WHERE title LIKE '%병원%';     -- ~500,000 (5%)
SELECT id, title FROM pet_post LIMIT 20;
SELECT MIN(created_at), MAX(created_at) FROM pet_post;
SELECT MIN(like_count), AVG(like_count), MAX(like_count) FROM pet_post;
SQL

echo "==> 완료."
