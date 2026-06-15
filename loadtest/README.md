# PetPost 1000만 건 더미 데이터 적재 (성능 측정 준비)

`PetPost` 대용량 쿼리 성능을 MySQL 안에서 직접 측정하기 위한 데이터 적재 도구.
**인덱스/쿼리 개선은 이 작업 범위가 아니다 — 데이터만 넣는다.**

## 구성 파일

| 파일 | 역할 |
|---|---|
| `../docker-compose.loadtest.yml` | 측정 전용 MySQL (포트 3307, buffer pool 128M, local_infile) — 기존 개발 DB와 완전 분리 |
| `schema.sql` | 측정 DB의 `users` / `pet_post` DDL (앱을 안 띄우므로 직접 적용) |
| `src/test/.../loadtest/DummyDataGenerator.java` | 순수 Java TSV 생성기 (Spring/JPA/DB 미사용) |
| `src/test/.../loadtest/TitleGenerator.java` | 제목 단어풀 조합 + 검색 키워드 비율 제어 |
| `src/test/.../loadtest/TsvWriter.java` | MySQL LOAD DATA 포맷 TSV 라이터 |
| `load.sql` | 참고용 LOAD DATA 구문 (실제 적재는 `run.sh`) |
| `run.sh` | end-to-end 자동화 (기동→DDL→생성→적재→검증) |

## 빠른 실행

```bash
# 프로젝트 루트에서
./loadtest/run.sh
```

## 수동 절차

```bash
# 1. 측정용 MySQL 기동 (기존 db 와 충돌 없음: 컨테이너 jipsamoye-db-loadtest, 포트 3307)
docker compose -f docker-compose.loadtest.yml up -d

# 2. DDL 적용
docker exec -i jipsamoye-db-loadtest \
  mysql --local-infile=1 -uroot -ploadtest jipsamoye_loadtest < loadtest/schema.sql

# 3. TSV 생성 (약 1만 users + 정확히 1000만 pet_post, pet_post.tsv 약 3GB)
./gradlew testClasses
java -cp build/classes/java/test:build/classes/java/main \
  com.jipsamoye.backend.loadtest.DummyDataGenerator loadtest/data

# 4. 적재 (users -> pet_post 순, FK 만족)
docker cp loadtest/data/users.tsv    jipsamoye-db-loadtest:/tmp/users.tsv
docker cp loadtest/data/pet_post.tsv jipsamoye-db-loadtest:/tmp/pet_post.tsv
docker exec -i jipsamoye-db-loadtest \
  mysql --local-infile=1 -uroot -ploadtest jipsamoye_loadtest -e "
LOAD DATA LOCAL INFILE '/tmp/users.tsv' INTO TABLE users CHARACTER SET utf8mb4
  FIELDS TERMINATED BY '\t' ESCAPED BY '\\\\' LINES TERMINATED BY '\n'
  (nickname, email, provider, provider_id, role, created_at, updated_at);
LOAD DATA LOCAL INFILE '/tmp/pet_post.tsv' INTO TABLE pet_post CHARACTER SET utf8mb4
  FIELDS TERMINATED BY '\t' ESCAPED BY '\\\\' LINES TERMINATED BY '\n'
  (user_id, title, content, image_urls, like_count, comment_count, created_at, updated_at);
"
```

## 검증 쿼리

```sql
SELECT COUNT(*) FROM pet_post;                                  -- 10,000,000
SELECT COUNT(*) - COUNT(DISTINCT title) FROM pet_post;          -- 제목 중복 (목표 < 1%)
SELECT COUNT(*) FROM pet_post WHERE title LIKE '%고양이%';        -- ~8% (~800,000)
SELECT COUNT(*) FROM pet_post WHERE title LIKE '%강아지%';        -- ~12% (~1,200,000)
SELECT COUNT(*) FROM pet_post WHERE title LIKE '%병원%';          -- ~5% (~500,000)
SELECT title FROM pet_post LIMIT 50;                            -- 다양성 육안 확인
SELECT MIN(created_at), MAX(created_at) FROM pet_post;          -- 최근 3년 분산
SELECT MIN(like_count), AVG(like_count), MAX(like_count) FROM pet_post; -- 멱법칙
-- 풀스캔 절대시간 (buffer pool 128M << 데이터 ~3.9GB 라 디스크 IO 강제)
EXPLAIN ANALYZE SELECT COUNT(*) FROM pet_post WHERE title LIKE '%고양이%' AND deleted_at IS NULL;
```

## 제목 다양성 (1000만 유니크성)

`TitleGenerator` 슬롯 구조: `[관형어] [반려동물] [상황] [변별요소] [부가표현] [어미]`
조합 수 = 80 × 116 × 91 × 235 × 30 × 50 ≈ **2977억** (1000만의 50배를 크게 상회 → 실측 중복률 0.004%).

- **변별요소(DISCRIMINATORS)** 는 펫 도메인에 자연스러운 숫자 표현 슬롯이다(235종):
  개월차/생후N개월, N살, 몸무게(N.Nkg), 함께한 일수/입양 일차/D+N, 주차. 한 제목에 1개만 들어간다.
  조합 수를 수백 배 늘리는 핵심 슬롯이다.
- 부가표현(EXTRAS)·변별요소(DISCRIMINATORS) 슬롯은 키워드("고양이"/"강아지"/"병원")를
  포함하지 않아 키워드 비율 제어(8%/12%/5%)에 영향이 없다.
- 슬롯별 단어풀도 대폭 확장됨: 관형어 80, 반려동물 116(고양이 36/강아지 36/기타 44),
  상황 91(병원 24/일반 67), 어미 50.

## 측정 조건 (재현성)

- MySQL 8.0, `innodb_buffer_pool_size=128M` (prod EC2 t2.micro 근사)
- `local_infile=1`
- 데이터: users 10,000 / pet_post 10,000,000, created_at 최근 3년 균등 분산,
  like_count 멱법칙(대부분 낮음), image_urls 30% 빈 배열
- 1000만 데이터(~3.9GB)가 buffer pool 128M 를 압도해 풀스캔 시 디스크 IO 가 강제됨(의도된 측정 조건)

## 정리

```bash
docker compose -f docker-compose.loadtest.yml down -v   # 볼륨까지 삭제
rm -rf loadtest/data                                    # 생성 TSV 삭제
```
