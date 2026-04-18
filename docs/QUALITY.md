# 집사모여 — 품질 현황

> 마지막 업데이트: 2026-04-18

## 도메인별 품질 등급

| 도메인 | 등급 | 테스트 | 비고 |
|--------|------|--------|------|
| auth | B | 없음 | 소셜 로그인 + 회원 탈퇴 로직 복잡, 테스트 필요 |
| user | B | 있음 | UserService 테스트 존재 |
| petPost | B | 없음 | CRUD + 이미지 연동 |
| comment | B | 없음 | 기본 CRUD |
| like | B | 없음 | 토글 로직 |
| follow | B | 없음 | 토글 로직 |
| image | B | 있음 | S3 Presigned URL 서비스 테스트 존재 |
| notification | B | 있음 | 알림 서비스 테스트 존재 |
| chat | B | 있음 | WebSocket 채팅 테스트 존재 |
| dm | B | 있음 | DM 서비스 테스트 존재 |
| board | B | 없음 | 자유게시판 CRUD (일반/질문 카테고리) |
| boardComment | B | 없음 | 자유게시판 댓글 CRUD |
| boardLike | B | 없음 | 자유게시판 좋아요 토글 |

### 등급 기준

| 등급 | 기준 |
|------|------|
| A | 테스트 충분, 아키텍처 규칙 준수, 기술 부채 없음 |
| B | 동작하지만 테스트 부족 또는 경미한 기술 부채 |
| C | 기술 부채 있음, 리팩터링 필요 |
| D | 긴급 개선 필요 |

## 알려진 기술 부채

| 항목 | 영향 도메인 | 설명 |
|------|------------|------|
| 타 도메인 Repository 직접 참조 | auth, user, petPost | AuthService가 5개 도메인 Repository 직접 참조. 이벤트 기반 위임으로 개선 필요 |
| user↔follow 순환 참조 | user, follow | UserService↔FollowRepository 상호 참조 |
| user↔petPost 순환 참조 | user, petPost | UserService↔PetPostRepository 상호 참조 |
