package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DmRoomResolver 단위 테스트.
 *
 * <p>resolver는 같은 빈의 트랜잭션 메서드를 프록시 경유로 호출하기 위해 자기 자신({@code self})을
 * 주입받는다. 단위 테스트에서는 self를 spy로 두고 트랜잭션 경계 메서드(findRoomId/createNormalized)를
 * 직접 stub하여 정규화·retry-on-conflict 조율 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DmRoomResolver - find-or-create 정규화·경쟁 조건 방어")
class DmRoomResolverTest {

    @Mock
    private DmRoomRepository dmRoomRepository;

    private DmRoomResolver resolver;
    private DmRoomResolver self;

    @BeforeEach
    void setUp() {
        // self는 실제 resolver를 가리키는 spy. 조율 로직(resolveOrCreateRoomId)은 진짜로 실행하고,
        // self 경유 트랜잭션 메서드만 spy로 stub한다.
        DmRoomResolver real = new DmRoomResolver(dmRoomRepository, null);
        self = spy(real);
        resolver = new DmRoomResolver(dmRoomRepository, self);
    }

    private User user(long id) {
        User u = mock(User.class);
        lenient().when(u.getId()).thenReturn(id);
        return u;
    }

    @Nested
    @DisplayName("기존 방 존재")
    class ExistingRoom {

        @Test
        @DisplayName("방이 이미 있으면 - 그 roomId를 반환하고 생성 미시도")
        void existingRoom_returnsId_noCreate() {
            User a = user(1L);
            User b = user(2L);
            doReturn(Optional.of(5L)).when(self).findRoomId(a, b);

            Long roomId = resolver.resolveOrCreateRoomId(a, b);

            assertThat(roomId).isEqualTo(5L);
            verify(self, never()).createNormalized(any(), any());
        }
    }

    @Nested
    @DisplayName("키 정규화 (min, max)")
    class Normalization {

        @Test
        @DisplayName("a.id > b.id 로 호출해도 - user1=min(id), user2=max(id) 순으로 저장")
        void create_normalizesToMinMax_whenAGreaterThanB() {
            User a = user(9L);
            User b = user(3L);

            DmRoom saved = mock(DmRoom.class);
            when(saved.getId()).thenReturn(42L);
            when(dmRoomRepository.save(any(DmRoom.class))).thenReturn(saved);

            Long roomId = self.createNormalized(a, b);

            assertThat(roomId).isEqualTo(42L);
            ArgumentCaptor<DmRoom> captor = ArgumentCaptor.forClass(DmRoom.class);
            verify(dmRoomRepository).save(captor.capture());
            DmRoom built = captor.getValue();
            assertThat(built.getUser1().getId()).isEqualTo(3L);
            assertThat(built.getUser2().getId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("a.id < b.id 로 호출해도 - 동일하게 user1=min, user2=max 순으로 저장")
        void create_normalizesToMinMax_whenALessThanB() {
            User a = user(3L);
            User b = user(9L);

            DmRoom saved = mock(DmRoom.class);
            when(saved.getId()).thenReturn(43L);
            when(dmRoomRepository.save(any(DmRoom.class))).thenReturn(saved);

            self.createNormalized(a, b);

            ArgumentCaptor<DmRoom> captor = ArgumentCaptor.forClass(DmRoom.class);
            verify(dmRoomRepository).save(captor.capture());
            DmRoom built = captor.getValue();
            assertThat(built.getUser1().getId()).isEqualTo(3L);
            assertThat(built.getUser2().getId()).isEqualTo(9L);
        }
    }

    @Nested
    @DisplayName("retry-on-conflict (동시 생성 경쟁)")
    class RetryOnConflict {

        @Test
        @DisplayName("createNormalized가 DataIntegrityViolationException이면 - 상대가 만든 방을 재조회해 그 roomId 반환")
        void create_conflict_requeriesAndReturnsExistingId() {
            User a = user(1L);
            User b = user(2L);

            // 최초 조회는 비어 있고(둘 다 생성 시도), 생성은 유니크 제약 위반,
            // 재조회 시 상대 트랜잭션이 만든 방이 보인다.
            doReturn(Optional.empty()).doReturn(Optional.of(77L)).when(self).findRoomId(a, b);
            doThrow(new DataIntegrityViolationException("duplicate key")).when(self).createNormalized(a, b);

            Long roomId = resolver.resolveOrCreateRoomId(a, b);

            assertThat(roomId).isEqualTo(77L);
            verify(self, times(2)).findRoomId(a, b);
        }

        @Test
        @DisplayName("생성 충돌 후 재조회도 비면 - BusinessException")
        void create_conflict_andRequeryEmpty_throws() {
            User a = user(1L);
            User b = user(2L);

            doReturn(Optional.empty()).doReturn(Optional.empty()).when(self).findRoomId(a, b);
            doThrow(new DataIntegrityViolationException("duplicate key")).when(self).createNormalized(a, b);

            assertThatThrownBy(() -> resolver.resolveOrCreateRoomId(a, b))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
