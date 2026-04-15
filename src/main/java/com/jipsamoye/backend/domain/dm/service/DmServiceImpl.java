package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.entity.DmMessage;
import com.jipsamoye.backend.domain.dm.entity.DmRoom;
import com.jipsamoye.backend.domain.dm.repository.DmMessageRepository;
import com.jipsamoye.backend.domain.dm.repository.DmRoomRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import com.jipsamoye.backend.global.util.ImageCdnConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmServiceImpl implements DmService {

    private final DmRoomRepository dmRoomRepository;
    private final DmMessageRepository dmMessageRepository;
    private final UserRepository userRepository;
    private final ImageCdnConverter imageCdnConverter;

    @Override
    public List<DmRoomResponse> getRooms(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return dmRoomRepository.findAllByUser(user).stream()
                .map(room -> {
                    User other = room.getUser1().getId().equals(userId) ? room.getUser2() : room.getUser1();
                    var lastMsg = dmMessageRepository.findFirstByRoomOrderByCreatedAtDesc(room);
                    long unread = dmMessageRepository.countUnread(room, user);

                    return DmRoomResponse.builder()
                            .roomId(room.getId())
                            .otherUserId(other.getId())
                            .otherUserNickname(other.getNickname())
                            .otherUserProfileImageUrl(other.getProfileImageUrl())
                            .lastMessage(lastMsg.map(DmMessage::getContent).orElse(null))
                            .lastMessageAt(lastMsg.map(DmMessage::getCreatedAt).orElse(null))
                            .unreadCount(unread)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public DmRoomResponse createRoom(Long userId, Long targetUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "자기 자신에게 DM을 보낼 수 없습니다.");
        }

        DmRoom room = dmRoomRepository.findByUsers(user, target)
                .orElseGet(() -> dmRoomRepository.save(DmRoom.builder()
                        .user1(user)
                        .user2(target)
                        .build()));

        return DmRoomResponse.builder()
                .roomId(room.getId())
                .otherUserId(target.getId())
                .otherUserNickname(target.getNickname())
                .otherUserProfileImageUrl(target.getProfileImageUrl())
                .unreadCount(0)
                .build();
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

        dmMessageRepository.markAllAsRead(room, user);

        Page<DmMessageResponse> messagePage = dmMessageRepository
                .findAllByRoomOrderByCreatedAtDesc(room, PageRequest.of(page, size))
                .map(msg -> DmMessageResponse.from(msg, imageCdnConverter));
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
        return DmMessageResponse.from(message, imageCdnConverter);
    }
}
