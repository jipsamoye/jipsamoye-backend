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
- IMPORTANT: 기능 구현 전 반드시 접근법을 먼저 제안하고 사용자 확인을 받는다.
  단순 버그 수정은 원인과 해결 방향만, 새 기능/리팩터링은 목표·접근법·완료 기준을 제시한다.
  접근법 제안 시 반드시: (1) 현재 방식보다 더 나은 대안은 없는지 검토하고,
  (2) 선택한 방식의 잠재적 문제점·트레이드오프를 함께 제시한다.
- IMPORTANT: 테스트 없이 커밋할 수 없다. 구현한 기능에 대해 반드시 단위 테스트를 작성한다.
  - 새 기능: 정상 동작 + 엣지 케이스
  - 버그 수정: 재현 테스트 (실패 → 수정 → 통과)
  - 리팩터링: 기존 동작 보존 확인
- IMPORTANT: 검증(`./gradlew build`)을 통과하지 않으면 커밋하지 않는다.
- 파일 내용을 임의로 채우지 않기
- 대화는 한국어로

## 새 도메인 추가 체크리스트
1. `domain/{도메인}/` 하위에 controller/, service/, repository/, entity/, dto/request/, dto/response/ 생성
2. Service는 인터페이스 + Impl 패턴 (상세: [코드 컨벤션](docs/CONVENTIONS.md))
3. Controller는 @RestController + ResponseEntity<ApiResponse<T>> 반환
4. Entity는 BaseEntity 상속, @Setter 금지
5. 테스트 코드 작성 후 `./gradlew test` 통과 확인
6. `docs/ARCHITECTURE.md` 도메인 맵 테이블에 새 도메인 추가
7. `docs/QUALITY.md` 품질 등급 테이블에 새 도메인 추가

## 상세 문서 (필요 시 참조)
- [아키텍처](docs/ARCHITECTURE.md) — 시스템 구조, 도메인 맵, 레이어 규칙, 패키지 구조
- [코드 컨벤션](docs/CONVENTIONS.md) — @Setter 금지, ApiResponse 래퍼, DTO 위치, Service 패턴
- [배포 & 운영](docs/DEPLOYMENT.md) — 배포 프로세스, 서버 정보, Discord 알림 형식
- [품질 현황](docs/QUALITY.md) — 도메인별 품질 등급, 알려진 기술 부채
- [PRD](docs/PRD.md) — 제품 요구사항 (서비스 개요, 기능 정의, 로드맵)
- [PR 템플릿](docs/PR_TEMPLATE.md) — PR 생성 시 사용하는 템플릿
