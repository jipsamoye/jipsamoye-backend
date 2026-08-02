package com.jipsamoye.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STOMP heartbeat 서버 설정 검증.
 *
 * <p>SimpleBroker는 heartbeat 기본값이 {0,0}(비활성)이라, 클라이언트(stompjs)가
 * heart-beat을 제안해도 협상 결과가 0이 되어 비정상 단절(네트워크 끊김·슬립)된
 * 세션을 서버가 TCP 타임아웃(수 분+)까지 감지하지 못한다. 이 테스트는 서버가
 * 25초/25초 heartbeat을 광고하고, heartbeat 발송에 필요한 TaskScheduler가
 * 브로커에 연결됐는지를 고정한다. (스케줄러 없이 값만 설정하면 부팅이 실패하거나
 * heartbeat이 동작하지 않는다.)
 *
 * <p>실제 세션 종료 동작(감지 창 = 협상 주기 × 3)은 Spring 프레임워크 영역이므로
 * 여기서는 설정 적용 여부만 검증한다.
 */
@SpringBootTest
@ActiveProfiles("contextload")
@DisplayName("STOMP heartbeat 설정 검증")
class WebSocketHeartbeatConfigTest {

    @Autowired
    private SimpleBrokerMessageHandler brokerMessageHandler;

    @Test
    @DisplayName("SimpleBroker가 25초/25초 heartbeat을 광고한다")
    void heartbeatValueIs25Seconds() {
        assertThat(brokerMessageHandler.getHeartbeatValue())
                .containsExactly(25_000L, 25_000L);
    }

    @Test
    @DisplayName("heartbeat 발송용 TaskScheduler가 브로커에 설정되어 있다")
    void taskSchedulerIsConfigured() {
        assertThat(brokerMessageHandler.getTaskScheduler()).isNotNull();
    }
}
