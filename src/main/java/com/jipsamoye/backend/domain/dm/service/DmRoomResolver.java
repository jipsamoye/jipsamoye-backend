package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 두 사용자 간 DM 방을 find-or-create한다.
 *
 * <p>경쟁 조건 방어:
 * <ol>
 *   <li><b>키 정규화</b>: 방을 항상 {@code (user1 = min(id), user2 = max(id))} 순서로 저장한다.
 *       유니크 제약 {@code (user1_id, user2_id)}이 컬럼 순서 종속이므로, 정규화 없이는
 *       동시 첫 메시지 시 (a,b)/(b,a)가 제약을 우회해 중복 방이 생긴다. 정규화하면
 *       두 트랜잭션이 동일한 (min,max)로 INSERT를 시도하므로 제약이 실제 중복을 막는다.</li>
 *   <li><b>retry-on-conflict</b>: 정규화 후에도 동시 INSERT 중 패배한 쪽은
 *       {@link DataIntegrityViolationException}을 받는다. 이를 흡수하고 재조회로 복구한다.</li>
 * </ol>
 *
 * <p><b>트랜잭션 경계</b>: 조회·생성·재조회를 각각 독립된 {@link Propagation#REQUIRES_NEW}
 * 트랜잭션으로 분리한다. INSERT 충돌 시 그 트랜잭션은 rollback-only로 닫히고 영속성 컨텍스트가
 * 폐기되므로, 깨끗한 새 트랜잭션에서 재조회해야 한다. (같은 트랜잭션에서 재조회하면 query
 * auto-flush가 깨진 엔티티를 다시 flush하려다 Hibernate AssertionFailure가 난다.)
 * 각 단계가 프록시 경유로 트랜잭션 어드바이스를 받도록 자기 자신을 {@code @Lazy} 주입해
 * 호출한다(자가 호출은 프록시를 우회하므로).
 *
 * <p>또한 이 빈은 무트랜잭션 진입점({@link #resolveOrCreateRoomId})을 제공한다. 호출자
 * (예: sendMessage의 외부 트랜잭션) 트랜잭션을 INSERT 충돌로 오염시키지 않기 위함이다.
 */
@Component
public class DmRoomResolver {

    private final DmRoomRepository dmRoomRepository;

    /**
     * 같은 빈의 트랜잭션 메서드를 프록시 경유로 호출하기 위한 자기 참조.
     * 자가 호출(this.method())은 AOP 프록시를 우회해 @Transactional 경계가 적용되지 않는다.
     * {@code @Lazy}는 생성자 파라미터에 직접 둬야 한다(Lombok @RequiredArgsConstructor는
     * @Lazy를 파라미터로 복사하지 않으므로 명시적 생성자를 사용한다).
     */
    private final DmRoomResolver self;

    @Autowired
    public DmRoomResolver(DmRoomRepository dmRoomRepository, @Lazy DmRoomResolver self) {
        this.dmRoomRepository = dmRoomRepository;
        this.self = self;
    }

    /**
     * 방을 find-or-create하고 roomId를 반환한다. 트랜잭션 없이 단계들을 조율한다.
     */
    public Long resolveOrCreateRoomId(User a, User b) {
        Optional<Long> existing = self.findRoomId(a, b);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return self.createNormalized(a, b);
        } catch (DataIntegrityViolationException e) {
            // 동시 생성 경쟁에서 패배 → 깨끗한 새 트랜잭션에서 상대가 만든 방을 재조회해 복구.
            return self.findRoomId(a, b)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BAD_REQUEST, "채팅방 생성에 실패했습니다."));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Long> findRoomId(User a, User b) {
        return dmRoomRepository.findByUsers(a, b).map(DmRoom::getId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createNormalized(User a, User b) {
        // (user1, user2)를 (min(id), max(id))로 정규화해 유니크 제약이 실제 중복을 막게 한다.
        User user1 = a.getId() <= b.getId() ? a : b;
        User user2 = a.getId() <= b.getId() ? b : a;
        return dmRoomRepository.save(DmRoom.builder()
                .user1(user1)
                .user2(user2)
                .build()).getId();
    }
}
