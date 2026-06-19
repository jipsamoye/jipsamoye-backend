package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.event.DmMessagesReadEvent;
import com.jipsamoye.backend.domain.dm.event.DmRoomUpdatedEvent;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmServiceImpl implements DmService {

    private final DmRoomRepository dmRoomRepository;
    private final DmMessageRepository dmMessageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DmRoomResolver dmRoomResolver;

    @Override
    public List<DmRoomResponse> getRooms(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 방 목록을 단일 프로젝션 쿼리로 가져와 N+1(방당 마지막 메시지·unread·LAZY 상대방)을 제거한다.
        // findRoomSummaries는 findAllByUser와 동일한 필터(참여 방·EXISTS message)·정렬(updatedAt DESC)을 보존한다.
        return dmRoomRepository.findRoomSummaries(userId).stream()
                .map(DmRoomResponseMapper::from)
                .toList();
    }

    /**
     * 채팅방을 resolve한다.
     * - 메시지가 있는 기존 방이면 그 방의 응답(roomId 포함)을 반환한다.
     * - 방이 없거나 빈 방(메시지 미발생)이면 DB에 저장하지 않고 draft 응답(roomId = null)을 반환한다.
     * 실제 방 생성(저장)은 첫 메시지 전송 시점({@link #sendMessage})에 이뤄진다.
     */
    @Override
    public DmRoomResponse createRoom(Long userId, String targetNickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findByNickname(targetNickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        validateDmTarget(user, target);

        DmRoom existing = dmRoomRepository.findByUsers(user, target).orElse(null);
        if (existing != null) {
            DmMessage lastMsg = dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(existing).orElse(null);
            if (lastMsg != null) {
                long unread = dmMessageRepository.countUnread(existing, user);
                return DmRoomResponseMapper.of(existing.getId(), target, lastMsg, unread);
            }
        }

        // 방이 없거나 빈 방 → 저장 없이 draft 응답(roomId=null, 마지막 메시지 없음)
        return DmRoomResponseMapper.of(null, target, null, 0);
    }

    @Override
    @Transactional
    public PageResponse<DmMessageResponse> getMessages(Long roomId, Long userId, int page, int size) {
        DmRoom room = dmRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "채팅방을 찾을 수 없습니다."));

        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        applyRead(room, user);

        Page<DmMessageResponse> messagePage = dmMessageRepository
                .findAllByRoomOrderByCreatedAtDesc(room, PageRequest.of(page, size))
                .map(DmMessageResponse::from);
        return PageResponse.from(messagePage);
    }

    @Override
    @Transactional
    public DmMessageResponse sendMessage(Long userId, Long roomId, String targetNickname, String content, String imageUrl) {
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        DmRoom room;
        if (roomId != null) {
            room = dmRoomRepository.findById(roomId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "채팅방을 찾을 수 없습니다."));
            if (!room.isParticipant(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        } else {
            if (targetNickname == null || targetNickname.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "대상 사용자를 지정해야 합니다.");
            }
            User target = userRepository.findByNickname(targetNickname)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            validateDmTarget(sender, target);

            // find-or-create는 키 정규화 + retry-on-conflict로 경쟁 조건을 막는 별도 트랜잭션에 위임한다.
            Long resolvedRoomId = dmRoomResolver.resolveOrCreateRoomId(sender, target);
            room = dmRoomRepository.findById(resolvedRoomId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "채팅방을 찾을 수 없습니다."));
        }

        DmMessage message = DmMessage.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .imageUrl(imageUrl)
                .build();

        dmMessageRepository.save(message);

        User partner = room.getUser1().getId().equals(userId) ? room.getUser2() : room.getUser1();
        eventPublisher.publishEvent(
                new DmRoomUpdatedEvent(room.getId(), List.of(sender.getId(), partner.getId())));

        return DmMessageResponse.from(message);
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long roomId) {
        DmRoom room = dmRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "채팅방을 찾을 수 없습니다."));

        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        applyRead(room, user);
    }

    /**
     * 읽음 처리 공통 로직. markAllAsRead가 1건 이상 갱신했을 때만 이벤트를 발행한다.
     * - {@link DmMessagesReadEvent}: 방 구독자에게 READ broadcast.
     * - {@link DmRoomUpdatedEvent}: 읽은 본인의 목록 배지(unreadCount) 갱신.
     */
    private void applyRead(DmRoom room, User user) {
        int updated = dmMessageRepository.markAllAsRead(room, user);
        if (updated > 0) {
            eventPublisher.publishEvent(
                    new DmMessagesReadEvent(room.getId(), user.getNickname(), LocalDateTime.now()));
            eventPublisher.publishEvent(
                    new DmRoomUpdatedEvent(room.getId(), List.of(user.getId())));
        }
    }

    private void validateDmTarget(User user, User target) {
        if (user.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "자기 자신에게 DM을 보낼 수 없습니다.");
        }
    }
}
