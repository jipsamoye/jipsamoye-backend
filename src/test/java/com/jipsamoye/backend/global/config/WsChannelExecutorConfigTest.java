package com.jipsamoye.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STOMP 채널 익스큐터 결선 검증.
 *
 * <p>버그 재현: 앱에 Executor 빈이 figurineExecutor 하나뿐이면 Boot의 기본
 * applicationTaskExecutor가 생성되지 않고, WebSocketMessagingAutoConfiguration이
 * "유일한 AsyncTaskExecutor"인 figurine 풀(스레드 1~2, 큐 20)을 STOMP 인바운드/
 * 아웃바운드 채널에 주입한다. 그 결과 알림·DM·채팅·heartbeat 전달 전부가 수 분씩
 * 걸리는 AI 이미지 생성 작업과 같은 풀에서 직렬화된다.
 *
 * <p>이 테스트는 채널 익스큐터가 figurine 풀과 분리되어 명시된
 * applicationTaskExecutor(TaskExecutorConfig)에 연결됐음을 인스턴스 동일성으로 고정한다.
 * (빈 정의 그래프는 정상으로 보여도 런타임 인스턴스가 다를 수 있어 identity 비교가 필수.)
 */
@SpringBootTest
@ActiveProfiles("contextload")
@DisplayName("WebSocket 채널 익스큐터 결선 검증")
class WsChannelExecutorConfigTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("STOMP 채널이 figurine 전용 풀을 쓰지 않는다")
    void channelsDoNotUseFigurineExecutor() {
        Object figurine = ctx.getBean("figurineExecutor");
        assertThat(ctx.getBean("clientInboundChannelExecutor")).isNotSameAs(figurine);
        assertThat(ctx.getBean("clientOutboundChannelExecutor")).isNotSameAs(figurine);
    }

    @Test
    @DisplayName("STOMP 채널은 명시 정의된 applicationTaskExecutor를 사용한다")
    void channelsUseApplicationTaskExecutor() {
        Object app = ctx.getBean("applicationTaskExecutor");
        assertThat(ctx.getBean("clientInboundChannelExecutor")).isSameAs(app);
        assertThat(ctx.getBean("clientOutboundChannelExecutor")).isSameAs(app);
    }
}
