package com.jipsamoye.backend;

import com.jipsamoye.backend.domain.dm.event.DmRoomUpdatedEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 풀 애플리케이션 컨텍스트 로드 스모크 테스트.
 *
 * <p>단위/슬라이스 테스트(@DataJpaTest 등)는 전체 컨텍스트를 부팅하지 않으므로,
 * "빌드는 통과하지만 앱 부팅(컨텍스트 refresh) 단계에서 죽는" 회귀를 잡지 못한다.
 * (예: @TransactionalEventListener 메서드에 잘못된 @Transactional propagation이 붙어
 * 컨텍스트 refresh 중 IllegalStateException 으로 부팅이 실패하는 버그.)
 *
 * <p>이 테스트는 @SpringBootTest 로 전체 컨텍스트를 실제로 부팅하여
 * WebSocket/STOMP 빈, 이벤트 리스너 등록을 포함한 부팅 단계를 그대로 거치게 한다.
 * 컨텍스트가 정상적으로 뜨면 통과한다. 외부 인프라(MySQL/S3/NAVER)는 contextload 프로필의
 * H2 + 더미 자격증명으로 대체하여 실제 인프라 없이 CI 에서 통과한다.
 */
@SpringBootTest
@ActiveProfiles("contextload")
@DisplayName("애플리케이션 컨텍스트 로드 스모크 테스트")
class ApplicationContextLoadTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("전체 애플리케이션 컨텍스트가 외부 인프라 없이 정상 부팅된다")
    void contextLoads() {
        // 컨텍스트 부팅이 성공하면(=@SpringBootTest 가 throw 없이 컨텍스트를 주입하면) 통과.
        assertThat(applicationContext).isNotNull();

        // 이번 회귀의 핵심: @TransactionalEventListener 리스너 빈이 부팅 단계에서 정상 등록되는지 확인.
        assertThat(applicationContext.getBean(DmRoomUpdatedEventListener.class)).isNotNull();

        // WebSocket/STOMP 메시지 브로커 인프라가 컨텍스트에 올라왔는지 확인.
        assertThat(applicationContext.containsBean("brokerMessagingTemplate")).isTrue();
    }
}
