# WebSocket 채널이 figurineExecutor를 공유하던 문제

- **발견**: 2026-08-02, STOMP heartbeat 도입 로컬 검증 중 세션 종료 로그가 `[figurine-1]` 스레드에서 찍히는 것을 추적
- **영향**: 알림·DM·오픈채팅·heartbeat 전달·유령 세션 정리 등 **모든 WebSocket 메시지 처리**가 AI 키캡 생성용 풀(스레드 1~2, 큐 20)에서 실행됨. 키캡 작업 2건이 동시에 돌면 그동안 WS 전체 정체, 큐 초과 시 거부
- **수정**: `TaskExecutorConfig`에 `applicationTaskExecutor` 명시 정의 (fix 커밋 참조)

## 원인 사슬 (전부 런타임 검증됨)

1. `figurineExecutor` 빈 추가 → Boot의 기본 `applicationTaskExecutor` 자동 생성이 취소됨 (`@ConditionalOnMissingBean(Executor.class)`)
2. Boot 3.2+ `WebSocketMessagingAutoConfiguration`은 AsyncTaskExecutor 빈이 **하나뿐이면 그것을** STOMP 인바운드/아웃바운드 채널에 `registration.executor(...)`로 주입 → 유일 후보가 figurineExecutor
3. `executor(...)` 경로는 `WebSocketConfig`의 `taskExecutor().corePoolSize(8~16)` 설정보다 우선 → 기존 head-of-line 완화 설정은 조용히 무력화된 상태였음 (실측 core=1)

증거: 컨텍스트에서 `clientInboundChannelExecutor` == `clientOutboundChannelExecutor` == `figurineExecutor` (인스턴스 identity 동일, 스레드 prefix `figurine-`). 빈 의존성 그래프(actuator/beans)는 정상으로 보였으므로 **선언 그래프가 아닌 런타임 identity로 검증해야 한다.**

## 수정 원리

`applicationTaskExecutor` 빈을 명시 정의하면 Boot의 선택 로직(후보 여러 개 → 이름 `applicationTaskExecutor` 우선)이 이것을 채널에 연결한다. figurineExecutor는 `@Async("figurineExecutor")` 전용으로 격리 (이름 없는 `@Async`는 코드베이스에 없음 확인). 풀 크기는 Boot 기본(core 8, 큐 무제한) = figurineExecutor 도입 이전과 동일한 구조.

## 재발 방지

- `WsChannelExecutorConfigTest`: 채널 익스큐터가 figurine과 분리·applicationTaskExecutor와 동일 인스턴스임을 고정
- 교훈: 전역 타입(Executor 등)의 빈을 추가하면 Boot 자동설정의 back-off 연쇄를 확인할 것
