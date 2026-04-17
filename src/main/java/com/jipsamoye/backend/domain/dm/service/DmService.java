package com.jipsamoye.backend.domain.dm.service;

import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.global.response.PageResponse;

import java.util.List;

public interface DmService {
    List<DmRoomResponse> getRooms(Long userId);
    DmRoomResponse createRoom(Long userId, String targetNickname);
    PageResponse<DmMessageResponse> getMessages(Long roomId, Long userId, int page, int size);
    DmMessageResponse sendMessage(Long userId, Long roomId, String content, String imageUrl);
}
