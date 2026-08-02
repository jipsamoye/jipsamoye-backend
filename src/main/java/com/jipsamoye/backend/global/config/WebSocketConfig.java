package com.jipsamoye.backend.global.config;

import com.jipsamoye.backend.global.config.security.DmSubscriptionAuthInterceptor;
import com.jipsamoye.backend.global.config.security.WebSocketAuthInterceptor;
import com.jipsamoye.backend.global.config.security.WebSocketPrincipalHandshakeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final DmSubscriptionAuthInterceptor dmSubscriptionAuthInterceptor;

    private TaskScheduler messageBrokerTaskScheduler;

    /**
     * heartbeat 발송용 스케줄러. {@code @EnableWebSocketMessageBroker}가 등록하는
     * messageBrokerTaskScheduler 빈을 재사용한다(새 스레드풀 불필요).
     * 이 설정 클래스 자체가 해당 빈을 만드는 구성의 입력이므로 생성자 주입 시
     * 순환 참조가 생긴다 — 반드시 @Lazy 세터 주입을 유지할 것.
     */
    @Autowired
    public void setMessageBrokerTaskScheduler(
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler taskScheduler) {
        this.messageBrokerTaskScheduler = taskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // heartbeat 25초: 백그라운드 탭 타이머 스로틀링(최대 1분 1회)에도
        // 감지 창(25초 × 3 = 75초)이 버티는 최소 주기. 클라이언트(stompjs 기본
        // 10000,10000)와 협상되어 양방향 25초로 동작하며, 비정상 단절 세션을
        // TCP 타임아웃(수 분+) 대신 최대 75초 내에 정리한다.
        config.enableSimpleBroker("/sub")
                .setHeartbeatValue(new long[]{25_000L, 25_000L})
                .setTaskScheduler(messageBrokerTaskScheduler);
        config.setApplicationDestinationPrefixes("/pub");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "https://jipsamoye.com",
                        "https://www.jipsamoye.com",
                        "https://jipsamoyefrontend.vercel.app",
                        "http://localhost:3000"
                )
                .addInterceptors(new WebSocketAuthInterceptor())
                .setHandshakeHandler(new WebSocketPrincipalHandshakeHandler())
                .withSockJS();
    }

    /**
     * 클라이언트 inbound 채널 설정 — {@link DmSubscriptionAuthInterceptor}로
     * DM 방 토픽 SUBSCRIBE 인가(타인 DM 도청 차단).
     *
     * <p>채널 스레드풀은 여기서 지정하지 않는다: Boot 3.2+의
     * {@code WebSocketMessagingAutoConfiguration}이 인바운드/아웃바운드 채널에
     * applicationTaskExecutor({@link TaskExecutorConfig})를 주입하며, 그 경로가
     * {@code registration.taskExecutor()} 설정보다 우선하므로 여기에 풀 크기를
     * 적어도 무시된다(과거 head-of-line 완화용 8~16 설정이 그렇게 죽은 설정이었다).
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(dmSubscriptionAuthInterceptor);
    }
}
