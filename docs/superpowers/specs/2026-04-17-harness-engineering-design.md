# 하네스 엔지니어링 적용 설계

> **버전:** v1.0
> **작성일:** 2026-04-17
> **참고:** [OpenAI Harness Engineering](https://openai.com/ko-KR/index/harness-engineering/)

---

## 1. 목표

OpenAI의 하네스 엔지니어링 개념을 집사모여 백엔드 프로젝트에 적용하여:
- 에이전트(Claude Code)가 리포지터리만으로 전체 컨텍스트를 이해할 수 있도록 지식 베이스 구조화
- 아키텍처 불변성을 코드로 강제하여 에이전트가 규칙을 위반하면 자동으로 감지
- Claude Code hooks로 작업 품질 자동 보장 루프 구축

---

## 2. 접근 방식

**점진적 하네스**: 기존 구조를 살리면서 단계적으로 하네스 레이어를 추가한다.

---

## 3. Phase 1: 지식 베이스 구조화

### 3.1 CLAUDE.md 리팩터링

현재 CLAUDE.md (99줄, 모든 규칙 인라인)를 **~60줄의 맵/목차**로 축소한다.

**CLAUDE.md에 유지할 내용:**
- 빌드 & 실행 명령어
- Git 워크플로우 (핵심 룰)
- 커밋 메시지 규칙
- 작업 방식 (사용자 확인 필수 등)
- 기술 스택 한줄 요약
- docs/ 하위 문서 안내 (맵 역할)

**docs/로 분리할 내용:**
- 코드 스타일 상세 → `docs/CONVENTIONS.md`
- 패키지 구조 상세 → `docs/ARCHITECTURE.md`
- Discord 알림 형식 → `docs/DEPLOYMENT.md`
- 배포 프로세스 상세 → `docs/DEPLOYMENT.md`

### 3.2 docs/ 디렉터리 구조

```
CLAUDE.md                          ← 맵/목차 (~60줄)
docs/
├── ARCHITECTURE.md                ← 시스템 아키텍처, 도메인 맵, 레이어 규칙
├── CONVENTIONS.md                 ← 코드 스타일, 패턴 상세
├── DEPLOYMENT.md                  ← 배포 프로세스, Discord 알림 형식
├── QUALITY.md                     ← 도메인별 품질 등급 (초기 버전)
├── PRD.md                         ← (기존 유지)
├── TRD.md                         ← (기존 유지)
├── PLAN.md                        ← (기존 유지)
└── superpowers/                   ← (기존 유지)
    ├── specs/
    └── plans/
```

### 3.3 새로 작성할 문서

**docs/ARCHITECTURE.md:**
- 시스템 아키텍처 다이어그램 (TRD 기반)
- 도메인 맵: 10개 도메인과 역할
- 레이어 구조: controller → service → repository → entity
- 의존성 방향 규칙
- global 패키지 역할과 범위

**docs/CONVENTIONS.md:**
- 엔티티 @Setter 금지, 메서드로 상태 변경
- Soft delete: deletedAt (LocalDateTime) 방식
- 응답 래퍼: ApiResponse, PageResponse
- Service 인터페이스 + Impl 패턴
- DTO 위치 규칙 (dto/request/, dto/response/)

**docs/DEPLOYMENT.md:**
- 배포 프로세스 상세 (feature → develop → main → 자동배포)
- SSH 접속 정보
- Discord 알림 형식 (에러, 배포 성공, 배포 실패)
- Docker + Nginx 구성

**docs/QUALITY.md:**
- 도메인별 품질 등급 (A~D)
- 테스트 커버리지 현황
- 알려진 기술 부채

---

## 4. Phase 2: ArchUnit 아키텍처 불변성 강제

### 4.1 의존성 추가

```groovy
// build.gradle
testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.0'
```

### 4.2 테스트 위치

```
src/test/java/com/jipsamoye/backend/global/architecture/ArchitectureTest.java
```

### 4.3 적용할 규칙 (5개)

**규칙 1: 레이어 의존성 방향 강제**
- Controller → Service → Repository 방향만 허용
- Controller가 Repository를 직접 참조하면 실패
- Service가 Controller를 참조하면 실패
- Entity는 Service/Controller를 참조할 수 없음
- 에러 메시지: "Controller는 Repository를 직접 참조할 수 없습니다. Service를 통해 접근하세요."

**규칙 2: 도메인 간 순환 참조 금지**
- slices().matching("..domain.(*)..").should().beFreeOfCycles() 활용
- 에러 메시지: "도메인 간 순환 참조가 발견되었습니다. 의존성 방향을 확인하세요."

**규칙 3: 엔티티 @Setter 금지**
- entity 패키지의 클래스에 @Setter 어노테이션 사용 금지
- 에러 메시지: "엔티티에 @Setter 사용이 금지되어 있습니다. 상태 변경은 메서드를 통해 수행하세요."

**규칙 4: Controller 반환 타입 강제**
- Controller의 public 메서드는 ApiResponse 또는 ResponseEntity를 반환해야 함
- 에러 메시지: "Controller 메서드는 반드시 ApiResponse 래퍼로 감싸서 반환해야 합니다."

**규칙 5: DTO 위치 강제**
- Request DTO는 dto/request/ 패키지에 위치
- Response DTO는 dto/response/ 패키지에 위치
- 에러 메시지: "DTO 클래스는 dto/request/ 또는 dto/response/ 패키지에 위치해야 합니다."

### 4.4 에이전트 친화적 에러 메시지 원칙

모든 ArchUnit 규칙의 에러 메시지는 다음을 포함한다:
1. 무엇이 잘못되었는지
2. 어떻게 고쳐야 하는지
→ 에이전트가 테스트 실패 메시지만 읽고도 자체 수정 가능

---

## 5. Phase 3: Claude Code Hooks

### 5.1 PreCommit Hook — 빌드 & 테스트 자동 검증

```json
{
  "hooks": {
    "PreCommit": [
      {
        "command": "./gradlew build",
        "description": "커밋 전 빌드 및 전체 테스트 (ArchUnit 포함) 실행"
      }
    ]
  }
}
```

커밋 전에 자동으로:
- 컴파일 확인
- ArchUnit 포함 전체 테스트 실행
- 실패 시 커밋 차단, 에이전트가 에러 메시지를 보고 수정

### 5.2 PostFileEdit Hook — 아키텍처 가이드 리마인더

Java 파일 수정 후:
- 수정한 파일이 속한 도메인/레이어 기반으로 관련 docs/ 문서 참조 리마인더 출력
- 에이전트가 항상 최신 규칙을 확인하도록 유도

### 5.3 PR 체크리스트

PR 생성 시 자동으로 체크리스트 포함:
- [ ] ArchUnit 테스트 통과
- [ ] 관련 docs/ 문서 업데이트 필요 여부 확인
- [ ] CLAUDE.md와의 일관성 체크

---

## 6. 구현 순서

1. Phase 1: 지식 베이스 구조화 (CLAUDE.md 리팩터링 + docs/ 문서 작성)
2. Phase 2: ArchUnit 설정 (의존성 추가 + 테스트 작성 + 기존 코드 위반 수정)
3. Phase 3: Claude Code Hooks 설정

---

## 7. 향후 확장 (이번 범위 밖)

- doc-gardening 자동화 (/loop 활용 문서 신선도 검증)
- 네이밍 컨벤션 ArchUnit 규칙 추가
- Spring 어노테이션 일관성 규칙 추가
- global 패키지 의존 방향 규칙 추가
- 품질 스코어 자동 업데이트
