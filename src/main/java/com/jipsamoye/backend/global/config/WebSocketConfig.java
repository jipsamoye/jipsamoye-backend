package com.jipsamoye.backend.global.config;

import com.jipsamoye.backend.global.config.security.DmSubscriptionAuthInterceptor;
import com.jipsamoye.backend.global.config.security.WebSocketAuthInterceptor;
import com.jipsamoye.backend.global.config.security.WebSocketPrincipalHandshakeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final DmSubscriptionAuthInterceptor dmSubscriptionAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/sub");
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
     * 클라이언트 inbound 채널 설정.
     * <ul>
     *   <li>{@link DmSubscriptionAuthInterceptor}: DM 방 토픽 SUBSCRIBE 인가(타인 DM 도청 차단).</li>
     *   <li>명시적 ThreadPoolTaskExecutor: 기본 소형 풀에서 @MessageMapping 핸들러의 동기 DB I/O가
     *       스레드를 점유해 무관한 사용자 메시지까지 직렬 지연되는 head-of-line blocking을 완화한다.</li>
     * </ul>
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(dmSubscriptionAuthInterceptor);
        registration.taskExecutor()
                .corePoolSize(8)
                .maxPoolSize(16)
                .queueCapacity(1000);
    }
}
