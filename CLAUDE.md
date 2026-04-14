# 집사모여 백엔드 프로젝트

## 빌드 & 실행
- `./gradlew build -x test` — 빌드 (테스트 제외)
- `./gradlew bootJar -x test` — JAR 생성
- `./gradlew test` — 테스트 실행
- 로컬 프로필: `application-local.yaml`, 운영: `application-prod.yaml`

## Git 워크플로우
- 브랜치: `feature/{기능명}` → `develop` 머지 → `develop`에서 `main` PR/머지
- IMPORTANT: develop에 먼저 머지한 후 main으로 PR. main에 직접 머지하지 않는다
- PR 머지 후 feature 브랜치 삭제
- 머지/푸시 후 develop 브랜치가 항상 최신 상태인지 확인
- main 푸시 → GitHub Actions 자동 배포 (EC2 + Docker)

## PR 템플릿
```
## 어떤 변경인가요?
## 변경 이유
## 변경 사항
## 테스트 방법
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

## 커밋 메시지
- 한글로 작성
- `feat:`, `fix:`, `refactor:`, `docs:` 접두사 사용
- 예: `feat: 좋아요 TOP 10 API 추가`

## 코드 스타일
- 기존 프로젝트 패턴을 따르되, 더 나은 방법이 있으면 제안하기
- 주석은 최소화 — 코드가 자명하면 주석 달지 않기
- 엔티티에 `@Setter` 사용 금지 → 메서드로 상태 변경
- Soft delete: `deletedAt` (LocalDateTime) 방식 사용
- 응답은 `ApiResponse` 래퍼로 감싸기
- 페이지네이션은 `PageResponse` 사용

## 패키지 구조
```
domain/{도메인}/
  ├── controller/
  ├── service/ (인터페이스 + Impl)
  ├── repository/
  ├── entity/
  └── dto/request/, dto/response/
global/
  ├── config/, exception/, response/, scheduler/
```

## 작업 방식
- IMPORTANT: 코드 변경 전 반드시 사용자 확인받기
- 파일 내용을 임의로 채우지 않기
- 기능 구현 시 더 나은 방법이 있으면 먼저 제안하고 확인받기
- 테스트 코드도 함께 작성하기
- 대화는 한국어로

## 기술 스택
- Spring Boot 3.5.13, Java 17, MySQL 8.0
- AWS S3 (Presigned URL), Docker, Nginx
- Swagger (springdoc-openapi)
