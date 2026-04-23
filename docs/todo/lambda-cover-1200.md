# 커버 이미지 `_1200.webp` 사이즈 추가

## 배경

집사모여 프로필 커버는 1200×400 영역에 표시된다. 현재 Lambda가 생성하는 800px 썸네일은 커버에 쓰면 **업스케일되어 흐리게 보임**. 반면 2048px 원본은 용량 낭비.

정답은 1200px 전용 변형을 만들어두는 것. 하지만 **모든 이미지에 1200이 필요한 건 아님** (피드 카드는 200, 상세는 800만 충분).

## 접근 방식 — 디렉터리별 SIZE_PRESETS

S3 key의 첫 prefix(`posts/` / `profiles/` / `covers/` / `dm/`)를 근거로 사이즈 분기.

### `resize.js` 수정

```javascript
const SIZE_PRESETS = {
  posts:    [{ suffix: '_200', width: 200 }, { suffix: '_800', width: 800 }],
  profiles: [{ suffix: '_200', width: 200 }, { suffix: '_800', width: 800 }],
  covers:   [{ suffix: '_200', width: 200 }, { suffix: '_800', width: 800 },
             { suffix: '_1200', width: 1200 }],
  dm:       [{ suffix: '_200', width: 200 }, { suffix: '_800', width: 800 }],
};

module.exports = { resizeToWebp, SIZE_PRESETS };
```

### `handler.js` 수정

```javascript
const { dir } = parseKey(key);
const sizes = SIZE_PRESETS[dir];
if (!sizes) {
  console.warn(`unknown dir preset: ${dir}`);
  return;
}
const thumbnails = await resizeToWebp(original, sizes);
```

## 배포

```bash
cd /Users/jys/jipsamoye.image-lambda
sam build
sam deploy --region ap-northeast-2 --parameter-overrides BucketName=jipsamoye-bucket \
  --capabilities CAPABILITY_IAM --resolve-s3 --no-confirm-changeset
```

**소요: 10~15분**

## 기존 이미지 대응

이 변경 이전에 올라온 cover 이미지는 `_1200`이 없음. 두 옵션:

1. **신규 업로드부터만** — 프론트 onError fallback이 원본을 쓰므로 UX 깨지지 않음
2. **일회성 배치 재처리** — 기존 cover 원본들을 re-upload 트리거로 Lambda 재실행

초기엔 1로 충분. 필요 시점에 배치 스크립트는 30분 안에 쓸 수 있음.

## 프론트 연동

백엔드 PR 후 프론트에 전달:

> "Cover 이미지는 이제 `_1200.webp` 도 사용 가능합니다. URL 컨벤션 동일(`.../thumbnails/{uuid}_1200.webp`). 프로필 커버 영역에선 `_1200`을 src로, `_800`을 srcSet 1x로 두고 원본을 onError fallback으로 쓰는 게 최적."

## 영향 파일

- `/Users/jys/jipsamoye.image-lambda/resize.js`
- `/Users/jys/jipsamoye.image-lambda/handler.js`
