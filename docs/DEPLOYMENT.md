# 집사모여 — 배포 & 운영

## 배포 프로세스

```
feature/{기능명}
    │
    ▼  (개발 완료 + 테스트 통과)
develop 머지
    │
    ▼  (develop push)
main PR 생성
    │
    ▼  (사용자 확인 필수)
main 머지
    │
    ▼
GitHub Actions 자동배포 (EC2 + Docker Blue-Green)
```

### 핵심 규칙

- **feature → main 직접 머지 절대 금지.** 반드시 develop에 먼저 머지한 후 develop → main PR을 생성한다.
- **main PR 머지 = 운영 배포.** 머지 전 반드시 사용자 확인을 받는다.
- PR 머지 후 feature 브랜치를 삭제한다.
- 머지/푸시 후 develop 브랜치가 항상 최신 상태인지 확인한다.

## 서버 정보

| 항목 | 값 |
|------|-----|
| Swagger UI | `http://{EC2_IP}/swagger-ui/index.html` |
| SSH 접속 | `ssh -i /Users/jys/jipsamoye.pem ubuntu@{EC2_IP}` |

> **IP 주소 유동적**: Elastic IP를 사용하지 않으므로 EC2를 껐다 켜면 IP가 매번 바뀐다.
> EC2 재시작 후 아래 2곳을 수동으로 업데이트해야 한다:
> 1. **GitHub Secrets** → `HOST` 값을 새 IP로 변경 (GitHub Actions 배포에 사용)
> 2. **Cloudflare DNS** → A 레코드의 IP를 새 IP로 변경

## 인프라 구성

| 컴포넌트 | 역할 |
|---------|------|
| EC2 + Docker | Spring Boot 애플리케이션 컨테이너 (IP 유동적) |
| MySQL 8.0 | Docker Compose로 실행되는 데이터베이스 |
| Nginx | 리버스 프록시 + Blue-Green 트래픽 전환 |
| AWS S3 | 이미지 저장소 (Presigned URL 업로드) |
| Cloudflare | CDN 캐싱 + DNS 관리 (A 레코드 → EC2 IP) |
| GitHub Actions | CI/CD 자동화 (main 머지 시 트리거) |
| Prometheus + Grafana | 애플리케이션 모니터링 |

## Discord 알림 형식

### 에러 알림

```
🚨 서버 에러 발생
━━━━━━━━━━━━━━━
⏰ 시간
📍 발생 위치 (파일:라인)
❌ 에러 코드: 메시지

🔗 요청 정보
  URL: 요청 URL
  IP: 클라이언트 IP
  User-Agent: 브라우저 정보

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
