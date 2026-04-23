# 서버단 이미지 크기 제한

## 배경

PRD에는 "이미지당 최대 10MB"로 제약이 명시돼 있지만 **코드에 반영되어 있지 않다**. 현재 Presigned URL 발급 시 `ImageServiceImpl.java`에서 확장자만 검증하고 파일 크기 제한은 없음.

## 리스크

- 프론트가 `browser-image-compression` 로 2048px/2MB 이하로 줄여 업로드하는 게 권장 경로지만, **프론트 검증은 우회 가능**
- 악의적 유저가 DevTools로 Presigned URL 가로채서 수백 MB 파일 업로드 가능 → S3 과금 + Lambda OOM 가능성
- 특히 Lambda 메모리 1GB 설정에서 100MB+ 파일 처리는 OOM 위험

## 해결 옵션

### 옵션 A. Presigned URL 서명 시 `contentLengthRange` 지정 (권장)

AWS SDK v2 `PutObjectPresignRequest.Builder`에는 content-length 제약을 직접 넣는 API가 없어서 **POST policy**로 전환하거나 condition 서명이 필요. 복잡도 중간.

### 옵션 B. S3 버킷 정책에 `s3:ObjectSize` 조건 추가 (추천)

```json
{
  "Sid": "DenyLargeUploads",
  "Effect": "Deny",
  "Principal": "*",
  "Action": "s3:PutObject",
  "Resource": "arn:aws:s3:::jipsamoye-bucket/*",
  "Condition": {
    "NumericGreaterThan": {
      "s3:ObjectSize": 10485760
    }
  }
}
```

10MB 초과 업로드 요청을 S3가 직접 거부. **가장 단순, 우회 불가**. 단 썸네일(Lambda가 생성, 보통 수백KB)은 영향 없음 — ObjectSize 조건이 10MB 초과에만 적용.

### 옵션 C. Lambda 초입에서 파일 크기 확인 후 초과 시 삭제

```javascript
const head = await s3.send(new HeadObjectCommand({ Bucket, Key }));
if (head.ContentLength > 10 * 1024 * 1024) {
  await s3.send(new DeleteObjectCommand({ Bucket, Key }));
  return;
}
```

업로드는 이미 성공한 후의 방어. 과금은 이미 발생한 시점이라 **덜 효율적**. 옵션 B와 병행 가능.

## 제안 순서

1. **옵션 B (S3 버킷 정책)** 을 먼저 적용 — 가장 단순·강력
2. 필요하면 옵션 A 추가 검토 (Presigned URL 응답 단계에서 명확한 에러 응답 주고 싶으면)

## 영향 파일

- AWS 콘솔의 S3 버킷 정책 (코드 변경 없음)
- 또는 IaC로 관리하고 싶으면 별도 Terraform/CloudFormation 파일
