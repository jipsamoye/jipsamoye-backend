package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.Role;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-2: DM 방 find-or-create 경쟁 조건 통합 테스트.
 *
 * <p>두 사용자가 동시에 첫 메시지를 보낼 때(=동시 find-or-create) 방이 정확히 하나만 생성되고
 * 양 호출이 동일한 roomId를 받는지 검증한다. 정규화로 (a,b)/(b,a) 중복 방 생성이 차단되고,
 * retry-on-conflict로 제약 위반 예외가 정상 흐름으로 흡수되어 호출자에게 전파되지 않아야 한다.
 *
 * <p>실제 트랜잭션 경계(REQUIRES_NEW)와 유니크 제약 동작을 확인해야 하므로 프록시된 실제 빈이
 * 필요하다. 따라서 @SpringBootTest(contextload, H2) 로 전체 컨텍스트를 부팅한다.
 * 테스트 메서드 자체는 트랜잭션을 열지 않아 각 스레드의 커밋이 서로 보인다.
 */
@SpringBootTest
@ActiveProfiles("contextload")
@DisplayName("DmRoomResolver 동시성 - find-or-create 경쟁 조건")
class DmRoomResolverConcurrencyTest {

    @Autowired
    private DmRoomResolver dmRoomResolver;

    @Autowired
    private DmRoomRepository dmRoomRepository;

    @Autowired
    private UserRepository userRepository;

    private User newUser() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .nickname("u-" + unique)
                .email(unique + "@test.com")
                .provider(Provider.GOOGLE)
                .providerId("pid-" + unique)
                .role(Role.USER)
                .build());
    }

    @Test
    @DisplayName("두 사용자가 (a,b)/(b,a) 순서로 동시 first-message - 방이 정확히 1개만 생성되고 같은 roomId 반환")
    void concurrentFirstMessage_createsExactlyOneRoom() throws Exception {
        User a = newUser();
        User b = newUser();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // 한쪽은 (a,b), 반대쪽은 (b,a) 순서로 호출해 인자 순서가 뒤바뀐 경쟁을 재현한다.
        Callable<Long> taskAB = () -> {
            barrier.await();
            return dmRoomResolver.resolveOrCreateRoomId(a, b);
        };
        Callable<Long> taskBA = () -> {
            barrier.await();
            return dmRoomResolver.resolveOrCreateRoomId(b, a);
        };

        Future<Long> f1 = pool.submit(taskAB);
        Future<Long> f2 = pool.submit(taskBA);

        Long roomId1 = f1.get();
        Long roomId2 = f2.get();
        pool.shutdown();

        // 두 호출 모두 예외 없이 동일한 roomId를 받아야 한다(중복 방·제약 위반 노출 없음).
        assertThat(roomId1).isNotNull();
        assertThat(roomId2).isNotNull();
        assertThat(roomId1).isEqualTo(roomId2);

        // 실제로 방이 정확히 1개만 존재하는지 양방향 조회로 확인.
        assertThat(dmRoomRepository.findByUsers(a, b)).isPresent();
        long roomCount = dmRoomRepository.findAll().stream()
                .filter(r -> r.isParticipant(a.getId()) && r.isParticipant(b.getId()))
                .count();
        assertThat(roomCount).isEqualTo(1L);
    }
}
