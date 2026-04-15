# 집사모여 백엔드 프로젝트

## 빌드 & 실행
- `./gradlew build -x test` — 빌드 (테스트 제외)
- `./gradlew bootJar -x test` — JAR 생성
- `./gradlew test` — 테스트 실행
- 로컬 프로필: `application-local.yaml`, 운영: `application-prod.yaml`

## Git 워크플로우
- 브랜치: `feature/{기능명}` → `develop` 머지 → `develop`에서 `main` PR/머지
- IMPORTANT: feature 브랜치를 main에 직접 머지하는 것은 절대 금지. 반드시 develop에 먼저 머지한 후 develop → main PR을 생성한다
- IMPORTANT: main PR 머지 = 운영 배포이므로 반드시 사용자 확인 후 머지
- PR 머지 후 feature 브랜치 삭제
- 머지/푸시 후 develop 브랜치가 항상 최신 상태인지 확인

## 배포
- 서버 주소: http://43.203.165.97/
- Swagger UI: http://43.203.165.97/swagger-ui/index.html
- SSH 접속: `ssh -i /Users/jys/jipsamoye.pem ubuntu@43.203.165.97`
- 배포 과정: feature → develop 머지 → develop push → main PR 생성 → 사용자 확인 → main 머지 → GitHub Actions 자동배포 (EC2 + Docker)

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
- IMPORTANT: 기능 구현 후 반드시 테스트 코드 작성 → 테스트 통과 확인 → 그 후 develop에 머지
- 대화는 한국어로

## Discord 알림 형식
에러 알림과 배포 알림은 아래 형식을 따른다.

### 에러 알림
```
🚨 서버 에러 발생
━━━━━━━━━━━━━━━
⏰ 시간
📍 발생 위치 (파일:라인)
❌ 에러 코드: 메시지

스택 트레이스 (최대 10줄)
```

### 배포 성공 알림
```
✅ 배포 완료
━━━━━━━━━━━━━━━
📦 커밋: 커밋 메시지
🔄 Blue → Green (또는 Green → Blue) 전환
⏰ 시간
```

### 배포 실패 알림
```
❌ 배포 실패
━━━━━━━━━━━━━━━
📦 커밋: 커밋 메시지
💥 원인: 실패 사유
⏰ 시간
```

## 기술 스택
- Spring Boot 3.5.13, Java 17, MySQL 8.0
- AWS S3 (Presigned URL), Docker, Nginx
- Swagger (springdoc-openapi)
