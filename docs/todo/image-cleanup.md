# 이미지 삭제 커버리지 확장

## 배경

현재 이미지 삭제 로직은 **PetPost 도메인만** 구현되어 있다 (`PetPostServiceImpl`이 `ImageService.deleteImages` 호출). 이번 PR(2026-04-23)에서 **원본 + 썸네일 2개 동시 삭제**로 확장했지만, 호출처는 여전히 PetPost 한 곳뿐이라 다른 도메인의 이미지 업로드 건은 삭제되지 않은 채 S3에 orphan으로 남는다.

## 누락된 도메인

### 1. Board (자유게시판) 이미지 삭제

`BoardService.deleteBoard`, `BoardService.updateBoard`에서 `ImageService.deleteImages` 호출하도록 연동.

- 삭제 시: 게시글의 모든 이미지 URL 삭제
- 수정 시: 제거된 이미지만 삭제 (기존 `PetPost` 방식 참고)

**영향 파일**: `domain/board/service/BoardServiceImpl.java`

### 2. Profile 이미지 변경 시 이전 이미지 정리

유저가 프로필 사진을 바꾸면 기존 `profiles/{userId}/oldUuid.webp`가 orphan이 된다.

- 현재 프로필 수정 API가 구현되어 있는지 먼저 확인 필요 (`UserService`)
- 있으면: 새 URL로 교체 전 기존 URL을 `ImageService.deleteImage` 호출
- 없으면: 프로필 수정 API 구현과 함께 이미지 정리도 같이

**영향 파일**: `domain/user/service/UserServiceImpl.java` (추정)

### 3. Cover 이미지 변경 시 이전 이미지 정리

Profile과 동일한 구조. 커버 이미지 업데이트 API에서 이전 이미지 삭제.

### 4. DM 이미지 메시지 삭제

DM 메시지 삭제 기능 유무 확인 후:

- 삭제 기능 있음: 해당 메시지의 이미지 URL 정리 연동
- 삭제 기능 없음: 보통 메신저류는 전송 후 삭제 불가 설계. 단 **DM 채팅방 전체 삭제** 또는 **회원 탈퇴 시** 일괄 정리 필요

### 5. 회원 탈퇴 시 유저의 모든 이미지 전체 정리

현재 `AuthServiceImpl.withdraw()`가 soft-delete 처리하지만 **S3 이미지는 그대로 남음**. GDPR·국내 개인정보보호법 관점에서 탈퇴 시 개인 데이터 완전 삭제가 요구될 수 있다.

- 해당 유저의 프로필/커버/게시글/DM 이미지 모두 S3에서 제거
- 각 도메인 엔티티 조회 후 URL 목록 수집 → `ImageService.deleteImages` 호출

**영향 파일**: `domain/auth/service/AuthServiceImpl.java`

## 구현 순서 제안

1. **Board 연동** (가장 단순, PetPost 패턴 그대로 복붙) — 30분
2. **회원 탈퇴 시 일괄 정리** (개인정보 관점에서 가장 중요) — 1시간
3. **Profile/Cover 변경 시 정리** (프로필 수정 API 구현 여부 확인 선행) — 30분~1시간
4. **DM** (기능 설계 재검토 후) — 보류

## 테스트

각 도메인 Service 테스트에서 Mockito로 `ImageService.deleteImages`가 **올바른 URL 리스트**로 호출되는지 검증. `ArgumentCaptor<List<String>>` 사용.

## 참고

- 원본 + 썸네일 동시 삭제 로직은 **`ImageServiceImpl`에서 이미 처리**. 호출자는 원본 URL만 전달하면 됨.
- S3 `DeleteObjects` API는 존재하지 않는 key에도 성공 응답 (idempotent). 썸네일이 생성되지 않은 엣지 케이스도 안전.
