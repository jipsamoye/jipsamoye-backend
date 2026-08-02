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
