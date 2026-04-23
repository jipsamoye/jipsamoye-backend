# 집사모여 — 인프라 구성

배포 절차는 [DEPLOYMENT.md](DEPLOYMENT.md), 추후 작업은 [todo/](todo/) 참조.

## 아키텍처

```
                  [브라우저]
                      │
  ┌───────────────────┼───────────────────┐
  ▼                   ▼                   ▼
www.jipsamoye   api.jipsamoye    images.jipsamoye
     │               │                   │
     ▼               ▼                   ▼
  [Vercel]    [Cloudflare Free]   [Cloudflare Free]
              DNS + CDN + SSL     + Worker(image-cdn)
                     │                   │
                     ▼                   ▼
              [EC2 Nginx :80]        [S3]
              Blue-Green 스위칭   jipsamoye-bucket
                     │                   │
                     ▼                   │ ObjectCreated 이벤트
              [Spring Boot :8080]        ▼
                     │            [AWS Lambda]
                     ▼            jipsamoye-image-resize
              [MySQL 8.0]              │
                                       ▼
                                    [S3] thumbnails/
```

## 도메인 → 서비스

| 도메인 | Origin |
|---|---|
| `www.jipsamoye.com` / `jipsamoye.com` | Vercel (프론트) |
| `api.jipsamoye.com` | EC2 Nginx → Spring Boot |
| `images.jipsamoye.com` | Worker `image-cdn` → S3 Static Website |

## AWS

- **계정**: `069765035859`, 리전 `ap-northeast-2`
- **프리티어 만료**: 2027-04-10
- **IAM 사용자** (용도별 분리):
  - `jipsamoye-admin` — 개발자 노트북, AdministratorAccess, 로컬 SAM 배포용
  - `jipsamoye-s3-user` — EC2 Spring Boot, S3 only, GitHub Secrets 경유
- **EC2**: `i-0c758c277baa5f044`, IP 유동적 (재부팅 시 DNS·GitHub Secrets 수동 갱신)
- **S3**: `jipsamoye-bucket`, Public Read, Static Website 활성
- **Lambda**: `jipsamoye-image-resize`, Node.js 20 arm64, SAM 배포 (별도 레포 `~/jipsamoye.image-lambda/`)

## Cloudflare

- **Free 플랜**
- **Worker `image-cdn`** — S3 static website 프록시 + Cache-Control 덮어쓰기
  - 200 응답: `max-age=31536000, immutable`
  - 4xx/5xx 응답: `max-age=60` (Lambda 썸네일 생성 대기 ~10초 커버)
  - 코드는 Cloudflare 대시보드에서만 관리 (레포 외부)

## S3 경로 규약

Spring Boot와 Lambda가 **동일한 규약**을 양쪽에서 하드코딩 — 변경 시 양쪽 동시 수정.

```
{dirName}/{userId}/{uuid}.{ext}                # 원본 (클라 업로드)
{dirName}/{userId}/thumbnails/{uuid}_200.webp  # Lambda 생성 (피드)
{dirName}/{userId}/thumbnails/{uuid}_800.webp  # Lambda 생성 (상세)

dirName: posts | profiles | covers | dm
userId:  Long (Spring CustomUserDetails)
uuid:    UUIDv4 소문자
ext:     jpg|jpeg|png|webp 소문자 강제
```

## 이미지 업로드 흐름

```
프론트 (2048px/q=0.80 WebP 압축 + EXIF 제거)
  → POST /api/images/presigned-url (백엔드)
  → Presigned URL 받음
  → PUT 직접 S3 (백엔드 경유 X)
  → S3 ObjectCreated 이벤트 → Lambda
  → Sharp 리사이즈 → S3 thumbnails/ 저장
  → CDN 캐시(1년 immutable)로 서빙
```

업로드 → 썸네일 가용: 보통 3~5초.

## 비용

모두 프리티어 내. **월 고정비 0원**.

주요 한도:
- EC2: 750h/월 (12개월 무료, 2027-04 이후 월 $8 수준)
- S3 스토리지: 5GB (12개월 무료)
- S3 PUT: 2,000회/월 (12개월 무료, 업로드당 3 PUT)
- Lambda: 1M 요청/월 (영구)
- Cloudflare: 대역폭 무제한 (영구)
- Worker: 10만 요청/일 (영구)

## 알려진 제약 / 개선 항목

- **EC2 IP 유동적** — 재부팅 시 수동 작업 필요
- **nginx upstream DNS 캐시** — `docker restart` 시 nginx도 재시작 안 하면 502 → [todo/nginx-upstream-resolver.md](todo/nginx-upstream-resolver.md)
- **S3 업로드 크기 제한 미적용** — [todo/image-size-enforcement.md](todo/image-size-enforcement.md)
- **Lambda 실패 실시간 알림 없음** — [todo/lambda-failure-alerts.md](todo/lambda-failure-alerts.md)
- **Worker 코드 레포 외부** — Cloudflare 대시보드에서만 편집 가능, 변경 이력 추적 어려움
