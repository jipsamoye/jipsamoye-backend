# AI 키캡 이미지 생성 기능 (figurine 도메인)

## Context

반려동물 자랑 SNS "집사모여"에 신규 기능 추가: 기존 글쓰기와 별도 버튼으로 반려동물 사진을 올리면 OpenAI 이미지 모델(gpt-image-1)로 **키보드 키캡 굿즈 스타일** 이미지를 생성해주고, 원하면 버튼 한 번으로 자랑 피드(petPost)에 자동 게시하는 기능.

인터뷰로 확정된 요구사항:
- **결과물**: 반려동물이 아티산 키캡 위 미니 피규어로 올라간 굿즈 스타일 이미지
- **처리 방식**: 비동기 + 폴링 (job 생성 → jobId 반환 → 프론트 2~3초 폴링)
- **입력 이미지** (사용자 최종 결정): 프론트가 기존 presigned 흐름으로 원본 업로드 → 백엔드가 **Lambda가 생성한 800px 썸네일**(`{dir}/{userId}/thumbnails/{uuid}_800.webp`)을 S3에서 직접 읽어 OpenAI에 전달
  - 썸네일 생성에 3~5초 걸리므로 **2초 간격 최대 5회 재시도**, 끝까지 없으면 **원본 폴백** (원본은 ContentLength 10MB 초과 시 거부)
  - 읽기 로직은 `FigurineImageStorage.downloadSource()` 한 곳에 캡슐화 (API/DB/프론트는 방식과 무관)
- **자동 게시**: 생성 완료 후 "게시글 자동올리기" 버튼 → 사용자 입력 없이 제목 "AI 키캡 자랑" 자동으로 petPost 생성
- **사용량 제한**: 없음 (추후 추가 예정)
- **네이밍**: figurine 유지 (`domain/figurine`, `FigurineJob`, `/api/figurines`)

이미지 처리 결정 (인터뷰 확정):
- **생성 장수**: 요청당 1장 (마음에 안 들면 새 요청으로 재생성)
- **품질/해상도**: 1024×1024, quality `medium` (~$0.04/장)
- **미게시 이미지 정리**: 하지 않음 — 게시 안 한 결과·원본 모두 S3에 보관 (추후 '내 생성 내역' 확장 여지, 스토리지 비용 미미)
- **결과 저장 경로**: `posts/{userId}/{uuid}.png` 재사용 — Lambda 썸네일·CDN·삭제 로직 수정 없이 동작

주의: 현재 브랜치(`feature/figurine-ai`)에 스테이징된 기존 figurine 코드는 **전부 삭제하고 새로 구현**한다 (사용자 지시).

## 전체 흐름

```
[프론트]
 1. 기존 presigned 흐름으로 사진 업로드 (dirName=posts) → imageUrl
 2. POST /api/figurines { sourceImageUrl } → job PENDING, jobId 즉시 반환
 3. GET /api/figurines/{jobId} 폴링 → 상태 + resultImageUrl
 4. POST /api/figurines/{jobId}/post → petPost 자동 생성

[백엔드 @Async]
 PENDING → PROCESSING
   → S3에서 thumbnails/{uuid}_800.webp getObject (2초 간격 최대 5회 재시도, 없으면 원본 폴백)
   → OpenAI POST /v1/images/edits (multipart: model=gpt-image-1, image, prompt, size=1024x1024, quality=medium)
   → 응답 b64_json 디코드 → S3 posts/{userId}/{uuid}.png 업로드 (Lambda 썸네일 자동 생성)
   → COMPLETED(resultImageUrl)   /   실패 시 FAILED(failReason)
```

## 구현 단계

### 0. 워킹트리 정리
- 기존 스테이징 코드는 사용자가 이미 정리 완료 (워킹트리 클린 확인됨)
- 단, 2026-07-22 세션에서 구현 중단된 미추적 파일 6개 삭제 필요:
  `rm -rf src/main/java/com/jipsamoye/backend/domain/figurine`
  (FigurineStatus, FigurineJob, FigurineJobRepository, OpenAiProperties, FigurineImageClient, OpenAiImageResponse — 전부 이 디렉터리 안에 있음)
- 삭제 후 `git status`로 `?? docs/code-review-2026-07/`(무관한 디렉터리)만 남는지 확인

### 1. 엔티티/리포지토리 — `domain/figurine/`
- `FigurineStatus` enum: `PENDING, PROCESSING, COMPLETED, FAILED`
- `FigurineJob` (BaseEntity 상속, @Setter 금지, 명시적 상태 전이 메서드):
  - `id`, `user`(ManyToOne LAZY), `status`, `sourceImageUrl`, `resultImageUrl`, `failReason`, `petPostId`(자동 게시 후 연결, nullable)
  - 메서드: `startProcessing()`, `complete(url)`, `fail(reason)`, `linkPetPost(id)`
- `FigurineJobRepository extends JpaRepository`

### 2. OpenAI 클라이언트 — `domain/figurine/client/`
- `FigurineImageClient` 인터페이스: `byte[] generateKeycapImage(byte[] sourceImage, String contentType)`
- `OpenAiFigurineImageClient` — **NaverApiClient 패턴 준수** (`src/main/java/com/jipsamoye/backend/domain/auth/client/NaverApiClient.java` 참고):
  - `RestClient.Builder` 주입 + `SimpleClientHttpRequestFactory` 타임아웃(connect 5s, read 120s), 테스트용 패키지-프라이빗 생성자
  - `POST https://api.openai.com/v1/images/edits`, multipart/form-data, `Authorization: Bearer {key}`
  - 응답 `data[0].b64_json` 디코드 → byte[] 반환, 실패/정책거부 시 BusinessException
- 프롬프트(상수): 사진 속 반려동물을 아티산 키캡 위의 귀여운 미니 피규어로 변환하는 영문 프롬프트. 예시 초안:
  > "Transform the pet in this photo into an adorable chibi-style miniature figurine sculpted on top of an artisan mechanical keyboard keycap. Product photography style, soft studio lighting, glossy resin texture, keyboard visible blurred in background."
- 설정 `@ConfigurationProperties(prefix = "openai")`: `api-key`(환경변수 `OPENAI_API_KEY`), `model=gpt-image-1`, `size=1024x1024`, `quality=medium`

### 3. S3 입출력 — `domain/figurine/` 내 컴포넌트 (기존 `S3Client` 빈 재사용)
- `FigurineImageStorage`:
  - `downloadSource(sourceImageUrl)`: CDN URL → key 변환(ImageServiceImpl.extractKeyFromUrl 로직 참고) → `thumbnails/{uuid}_800.webp` key로 getObject, NoSuchKey면 2초 간격 최대 5회 재시도 → 그래도 없으면 원본 key 폴백(ContentLength 10MB 초과 거부)
  - `uploadResult(userId, byte[] png)`: `posts/{userId}/{uuid}.png` putObject(contentType image/png) → CDN URL 반환
- CDN 경유 조회 금지, S3 직접 접근 (Worker가 4xx를 60초 캐시하므로 오염 방지)

### 4. 비동기 처리
- `FigurineAsyncConfig`: `@EnableAsync` + 전용 `ThreadPoolTaskExecutor` 빈(`figurineExecutor`, core 1 / max 2 / queue 20) — 프로젝트에 기존 @EnableAsync 없음, 신규 도입
- `FigurineJobProcessor` (`@Async("figurineExecutor")`):
  - 짧은 트랜잭션 분리: ① PROCESSING 마킹 → ② (트랜잭션 밖) S3 다운로드 + OpenAI 호출 + S3 업로드 → ③ COMPLETED/FAILED 마킹
  - 모든 예외 catch → FAILED(사유) 저장, 로그

### 5. 서비스/컨트롤러
- `FigurineService` 인터페이스 + `FigurineServiceImpl`:
  - `createJob(request, userId)`: sourceImageUrl 검증(우리 CDN URL만 허용) → job 저장 → processor 비동기 호출 → jobId 반환
  - `getJob(jobId, userId)`: 본인 job만 조회(아니면 FORBIDDEN). **PROCESSING/PENDING인 채 5분 경과 시 FAILED로 전환** (서버 재시작 유실 방어)
  - `publishJob(jobId, userId)`: COMPLETED 상태 검증, `petPostId` 이미 있으면 에러(중복 게시 방지) → 기존 `PetPostService.createPost` 재사용 (제목 "AI 키캡 자랑", content null, imageUrls=[resultImageUrl]) → `linkPetPost`
- `FigurineController` (`@RestController`, `ResponseEntity<ApiResponse<T>>` 래퍼):
  - `POST /api/figurines` — 생성 요청 (`FigurineJobCreateRequest{sourceImageUrl}`)
  - `GET /api/figurines/{jobId}` — 상태 조회 (`FigurineJobResponse{jobId, status, resultImageUrl, failReason, petPostId}`)
  - `POST /api/figurines/{jobId}/post` — 자동 게시 (petPostId 반환)
- `ErrorCode` 추가: `FIGURINE_JOB_NOT_FOUND(404)`, `FIGURINE_JOB_NOT_COMPLETED(400)`, `FIGURINE_ALREADY_POSTED(409)`, `FIGURINE_GENERATION_FAILED(502)`

### 6. 설정 파일
- `application.yaml`(공통): openai 모델/사이즈/품질 기본값
- `application-local.yaml` / `application-prod.yaml`: `openai.api-key: ${OPENAI_API_KEY}` — 키 값은 파일에 넣지 않음 (배포 환경변수는 사용자가 별도 설정)

### 7. 테스트 (커밋 전 필수)
- `FigurineServiceImplTest`: 정상 생성 흐름, 본인 아닌 job 조회 → FORBIDDEN, 5분 초과 PROCESSING → FAILED 전환, 미완료 job 게시 시도 → 에러, 중복 게시 → 에러, 자동 게시 성공 시 PetPostService 호출 검증 (Mockito, 기존 ServiceImplTest 스타일)
- `OpenAiFigurineImageClientTest`: MockRestServiceServer로 정상 응답(b64_json 디코드)·에러 응답 (NaverApiClient 테스트 패턴)
- `FigurineJobProcessorTest`: 성공 → COMPLETED, OpenAI 예외 → FAILED + 사유 저장
- `FigurineJobTest`(엔티티): 상태 전이 검증
- ArchUnit 통과 확인 (`./gradlew test`)

### 8. 문서
- `docs/ARCHITECTURE.md` 도메인 맵에 figurine 추가
- `docs/QUALITY.md` 품질 등급 테이블에 figurine 추가

## 알려진 트레이드오프 (사용자와 합의됨)
- `@Async` 메모리 큐: 배포/재시작 시 진행 중 job 유실 → 5분 타임아웃 규칙 + 프론트 재시도로 방어 (단발성 작업이라 충분)
- 사용량 무제한: 과금 리스크 → OpenAI 대시보드 월 지출 한도 설정 권장, 쿼터는 추후 추가
- 원본 사진이 게시 없이도 S3에 남음(고아 이미지): 기존 프로젝트 전반의 알려진 제약과 동일, 별도 처리 안 함

## 검증 방법
1. `./gradlew build` (전체 테스트 + ArchUnit 포함) 통과
2. 로컬 수동 검증(OPENAI_API_KEY 설정 시): 로컬 부팅 → presigned로 사진 업로드 → `POST /api/figurines` → 폴링으로 COMPLETED 확인 → 결과 이미지 URL 열어 키캡 스타일 확인 → `POST /{jobId}/post` → 자랑 피드에 게시글 확인
3. API 키 없이는 MockRestServiceServer 테스트로 클라이언트 로직 검증

## 커밋/브랜치
- 현재 브랜치 `feature/figurine-ai`에서 작업, 커밋 메시지 한글 (`feat: AI 키캡 이미지 생성 기능 추가`)
- 완료 후 develop 머지 → develop→main PR은 사용자 확인 후 (CLAUDE.md 규칙)
