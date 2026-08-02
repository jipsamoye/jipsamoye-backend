package com.jipsamoye.backend.global.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 범용 비동기 작업 풀(applicationTaskExecutor) 명시 정의.
 *
 * <p>Boot는 앱에 Executor 빈이 하나라도 있으면(현재 figurineExecutor) 기본
 * applicationTaskExecutor 자동 생성을 포기한다. 그 상태에서
 * {@code WebSocketMessagingAutoConfiguration}은 "유일한 AsyncTaskExecutor" 빈을
 * STOMP 인바운드/아웃바운드 채널에 주입하므로, 모든 WebSocket 메시지 처리
 * (알림·DM·채팅·heartbeat 전달·세션 정리)가 figurine 전용 풀(스레드 1~2, 큐 20)을
 * 수 분씩 걸리는 AI 이미지 생성 작업과 공유하게 된다.
 *
 * <p>이 빈을 명시하면 Boot가 AsyncTaskExecutor 후보 여러 개 중 이 이름
 * ("applicationTaskExecutor")을 선택해 채널에 연결하고, figurineExecutor는
 * {@code @Async("figurineExecutor")} 전용으로 격리된다. 풀 크기는 Boot 기본
 * ({@code spring.task.execution.*}: core 8, 큐 무제한)을 그대로 따른다.
 * 검증: {@code WsChannelExecutorConfigTest}, 이슈:
 * {@code docs/issues/2026-08-02-ws-channel-figurine-executor.md}
 */
@Configuration
public class TaskExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }
}
