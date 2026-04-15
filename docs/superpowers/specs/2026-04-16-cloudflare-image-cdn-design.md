# Cloudflare 이미지 CDN 설계

## 배경

현재 이미지가 S3에서 원본 그대로 직접 서빙되어 성능 문제가 발생하고 있다.
- 캐싱 없이 매번 S3에서 다운로드
- 원본 이미지 크기 그대로 전송 (2~5MB)
- CDN 없이 서울 리전 S3에만 의존

## 목표

- Cloudflare CDN으로 이미지 캐싱 적용 (반복 요청 속도 개선)
- 프론트엔드 이미지 리사이즈로 전송 크기 감소 (최초 요청 속도 개선)
- 추가 비용 없음 (Cloudflare 프리 플랜 대역폭 무제한)

## 설계

### 1. Cloudflare 인프라 설정

**DNS 설정:**
- `images.jipsamoye.com` CNAME → `jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com`
- 프록시 모드 ON (주황색 구름) — Cloudflare CDN 캐싱 활성화

**Cache Rules:**
- `images.jipsamoye.com/*` 경로에 캐시 TTL 1년 설정
- 이미지가 UUID 기반이라 캐시 무효화 불필요 (이미지 수정 시 새 UUID 생성)

**CORS:**
- 변경 없음. Cloudflare→S3는 서버 간 통신이라 CORS 적용 안 됨
- 기존 S3 CORS(PUT 업로드용)는 그대로 유지

**이미지 조회 흐름:**
```
브라우저 → images.jipsamoye.com → Cloudflare 엣지 캐시 확인
  ├─ 캐시 히트 → 바로 응답
  └─ 캐시 미스 → S3에서 가져옴 → 캐시 저장 → 응답
```

### 2. 백엔드 변경사항

#### 2-1. CDN 설정값 추가

`application-prod.yaml`:
```yaml
cdn:
  image-base-url: https://images.jipsamoye.com
```

`application-local.yaml`:
```yaml
cdn:
  image-base-url: https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com
```

#### 2-2. URL 컨버터

S3 URL ↔ CDN URL 변환 유틸리티 생성:
- `toCdnUrl(String s3Url)`: S3 도메인 → CDN 도메인 변환
- 이미 CDN URL이면 그대로 반환 (멱등성)
- null/빈 문자열은 그대로 반환

```
입력: https://jipsamoye-bucket.s3.ap-northeast-2.amazonaws.com/posts/1/uuid.jpg
출력: https://images.jipsamoye.com/posts/1/uuid.jpg
```

#### 2-3. 응답 DTO 적용

이미지 URL이 포함된 모든 Response DTO에서 컨버터 적용:
- `PetPostResponse` — imageUrls (List)
- `UserResponse` — profileImageUrl, coverImageUrl
- `CommentResponse` — profileImageUrl
- `DmMessageResponse` — imageUrl
- `FollowUserResponse` — profileImageUrl
- `ChatMessageResponse` — profileImageUrl

#### 2-4. Presigned URL 응답 변경

`ImageServiceImpl.generatePresignedUrl()`에서:
- `presignedUrl`: S3 Presigned URL 그대로 (업로드는 S3 직접)
- `imageUrl`: CDN URL로 변경 (`https://images.jipsamoye.com/...`)

#### 2-5. 이미지 삭제 역변환

`extractKeyFromUrl()`에서 CDN URL과 기존 S3 URL 모두 key 추출 가능하도록:
```
https://images.jipsamoye.com/posts/1/uuid.jpg → posts/1/uuid.jpg
https://jipsamoye-bucket.s3..../posts/1/uuid.jpg → posts/1/uuid.jpg
```

### 3. 프론트엔드 변경사항 (전달 사항)

- 이미지 업로드 전 리사이즈: 최대 1200px, WebP 변환, 품질 80%
- Presigned URL 요청 시 `ext: "webp"`로 변경
- API 응답 URL은 이미 CDN URL이므로 그대로 사용

## 작업 순서

1. Cloudflare에 `images.jipsamoye.com` 서브도메인 설정
2. CDN 경유로 기존 S3 이미지 접근 가능한지 확인
3. 백엔드: CDN 설정값 + URL 컨버터 구현
4. 백엔드: Response DTO에 컨버터 적용
5. 백엔드: Presigned URL 응답 변경 + 삭제 역변환
6. 백엔드: 테스트 코드 작성 + 검증
7. 백엔드: develop → main 배포
8. 프론트엔드: 이미지 리사이즈 로직 추가

## 전후 비교

|  | Before | After |
|--|--------|-------|
| 이미지 크기 | 2~5MB (원본) | 100~300KB (리사이즈) |
| 캐싱 | 없음 | Cloudflare 엣지 캐싱 |
| 응답 서버 | S3 서울 리전만 | Cloudflare 전 세계 엣지 |
| 반복 요청 | 매번 S3 접근 | CDN 캐시에서 즉시 응답 |
| 추가 비용 | - | 없음 (Cloudflare 프리 플랜) |
