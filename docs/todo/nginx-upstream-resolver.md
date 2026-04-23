# Nginx upstream DNS 재조회 설정

## 배경

2026-04-23 발생한 502 이슈(복구 완료)의 근본 원인.

Docker 환경에서 `docker restart jipsamoye-app-blue` 같은 단순 재시작으로 컨테이너 IP가 바뀌면, nginx는 startup 시점에 캐시한 옛 IP로 계속 연결 시도 → `Host is unreachable` → 502.

현재 운영 방식: **"app 재시작 시 nginx도 함께 재시작"** 이라는 암묵적 룰. 문서화되어 있지 않고 잊기 쉬움.

## 해결 방향

nginx config에 `resolver` 디렉티브 + 변수 기반 `proxy_pass`를 추가해 **런타임 DNS 재조회**를 강제한다.

### `nginx/nginx.conf` 수정

```nginx
server {
    listen 80;

    # Docker 내부 DNS (127.0.0.11) 사용, 10초마다 재조회
    resolver 127.0.0.11 valid=10s ipv6=off;

    location /ws {
        set $upstream_ws http://jipsamoye-app-blue:8080;  # 변수화 — runtime resolve 유도
        proxy_pass $upstream_ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;
    }

    location / {
        set $upstream http://jipsamoye-app-blue:8080;
        proxy_pass $upstream;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 주의 — Blue-Green 배포 스크립트와의 호환성

현재 blue/green 전환은 `upstream.conf`를 교체(blue→green) + nginx reload하는 방식. 변수화한 `$upstream` 으로 전환하면 **upstream.conf 교체 패턴이 깨질 수 있음**.

옵션:
- (A) `set` 변수에 환경변수 또는 파일 include로 현재 활성 컨테이너 주입 → Blue-Green 전환 스크립트 수정 함께
- (B) `upstream.conf`를 그대로 유지하되 블록 안에 `resolver + zone` 추가 — nginx Plus 기능이라 Open Source에선 제한적

**A가 정석. 단 Blue-Green 파이프라인 재검토 필요.**

## 배포

- 변경된 nginx.conf를 `scripts/deploy.sh` 로 EC2에 배포하거나 수동 SCP
- `docker restart jipsamoye-nginx` 로 반영

## 우선순위 근거

- 현 수동 절차("nginx도 같이 재시작") 로도 충분히 운영 가능
- 배포 자동화가 성숙해지고 재시작 자동 트리거가 늘어날 때 우선순위 ↑
- 그 전까진 알고만 있으면 됨

## 면접 활용 관점

이 기록 자체가 **"production에서 502 터지고 원인 분석해서 장기 개선안 문서화한 경험"** 이 됨. 해결한 임시 대응 + 장기 개선안을 둘 다 말할 수 있는 드문 스토리.

## 영향 파일

- `/Users/jys/jipsamoye.backend/nginx/nginx.conf`
- `/Users/jys/jipsamoye.backend/nginx/upstream*.conf` (재설계 가능성)
- `/Users/jys/jipsamoye.backend/scripts/deploy.sh` (Blue-Green 전환 로직)
