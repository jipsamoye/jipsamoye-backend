# 집사모여 백엔드 프로젝트

> 반려동물 커뮤니티 — Spring Boot 3.5.13, Java 17, MySQL 8.0, Cloudflare CDN

## 빌드 & 실행
- `./gradlew build -x test` — 빌드 (테스트 제외)
- `./gradlew bootJar -x test` — JAR 생성
- `./gradlew test` — 테스트 실행 (ArchUnit 아키텍처 테스트 포함)
- 로컬 프로필: `application-local.yaml`, 운영: `application-prod.yaml`

## Git 워크플로우
- 브랜치: `feature/{기능명}` → `develop` 머지 → `develop`에서 `main` PR/머지
- IMPORTANT: feature 브랜치를 main에 직접 머지하는 것은 절대 금지. 반드시 develop에 먼저 머지한 후 develop → main PR을 생성한다
- IMPORTANT: main PR 머지 = 운영 배포이므로 반드시 사용자 확인 후 머지
- PR 머지 후 feature 브랜치 삭제
- 머지/푸시 후 develop 브랜치가 항상 최신 상태인지 확인

## 커밋 메시지
- 한글로 작성
- `feat:`, `fix:`, `refactor:`, `docs:` 접두사 사용
- 예: `feat: 좋아요 TOP 10 API 추가`

## 작업 방식
- IMPORTANT: 코드 변경 전 반드시 사용자 확인받기
- IMPORTANT: 기능 구현 후 반드시 테스트 코드 작성 → 테스트 통과 확인 → 그 후 develop에 머지
- 파일 내용을 임의로 채우지 않기
- 기능 구현 시 더 나은 방법이 있으면 먼저 제안하고 확인받기
- 대화는 한국어로

## 상세 문서 (필요 시 참조)
- [아키텍처](docs/ARCHITECTURE.md) — 시스템 구조, 도메인 맵, 레이어 규칙, 패키지 구조
- [코드 컨벤션](docs/CONVENTIONS.md) — @Setter 금지, ApiResponse 래퍼, DTO 위치, Service 패턴
- [배포 & 운영](docs/DEPLOYMENT.md) — 배포 프로세스, 서버 정보, Discord 알림 형식
- [품질 현황](docs/QUALITY.md) — 도메인별 품질 등급, 알려진 기술 부채
- [PRD](docs/PRD.md) — 제품 요구사항 (서비스 개요, 기능 정의, 로드맵)

## PR 템플릿
```
## 어떤 변경인가요?
## 변경 이유
## 변경 사항
## 테스트 방법
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```
