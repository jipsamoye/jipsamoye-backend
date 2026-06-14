-- PetPost 제목 검색 FULLTEXT(ngram) 인덱스 도입
-- 원인: 기존 검색이 title LIKE '%kw%' 풀스캔(인덱스 미사용)이라 최악 5.8초.
--       무한스크롤이라 COUNT 불필요 → FULLTEXT 구문검색(ngram 파서) + Slice로 개편.
-- 실행 순서: DB 변경 → 백엔드 배포 (인덱스는 ddl-auto가 건드리지 않으므로 마이그레이션으로만 관리)

ALTER TABLE pet_post
    ADD FULLTEXT INDEX ft_title (title) WITH PARSER ngram;
