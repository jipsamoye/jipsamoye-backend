package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.event.DmMessagesReadEvent;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.follow.repository.FollowRepository;
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
    private final FollowRepository followRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<DmRoomResponse> getRooms(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return dmRoomRepository.findAllByUser(user).stream()
                .map(room -> {
                    User other = room.getUser1().getId().equals(userId) ? room.getUser2() : room.getUser1();
                    var lastMsg = dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(room);
                    long unread = dmMessageRepository.countUnread(room, user);

                    return new DmRoomResponse(
                            room.getId(),
                            other.getNickname(),
                            other.getProfileImageUrl(),
                            lastMsg.map(DmMessage::getContent).orElse(null),
                            lastMsg.map(DmMessage::getCreatedAt).orElse(null),
                            unread
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public DmRoomResponse createRoom(Long userId, String targetNickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findByNickname(targetNickname)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "자기 자신에게 DM을 보낼 수 없습니다.");
        }

        if (!followRepository.existsByFollowerAndFollowing(user, target)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "팔로우한 사용자에게만 DM을 보낼 수 있습니다.");
        }

        DmRoom room = dmRoomRepository.findByUsers(user, target)
                .orElseGet(() -> dmRoomRepository.save(DmRoom.builder()
                        .user1(user)
                        .user2(target)
                        .build()));

        return new DmRoomResponse(
                room.getId(),
                target.getNickname(),
                target.getProfileImageUrl(),
                null,
                null,
                0
        );
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

        int updated = dmMessageRepository.markAllAsRead(room, user);
        if (updated > 0) {
            eventPublisher.publishEvent(new DmMessagesReadEvent(roomId, user.getNickname(), LocalDateTime.now()));
        }

        Page<DmMessageResponse> messagePage = dmMessageRepository
                .findAllByRoomOrderByCreatedAtDesc(room, PageRequest.of(page, size))
                .map(DmMessageResponse::from);
        return PageResponse.from(messagePage);
    }

    @Override
    @Transactional
    public DmMessageResponse sendMessage(Long userId, Long roomId, String content, String imageUrl) {
        DmRoom room = dmRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "채팅방을 찾을 수 없습니다."));

        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        DmMessage message = DmMessage.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .imageUrl(imageUrl)
                .build();

        dmMessageRepository.save(message);
        return DmMessageResponse.from(message);
    }
}
