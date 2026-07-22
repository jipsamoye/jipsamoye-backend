package com.jipsamoye.backend.domain.dm.controller;

import com.jipsamoye.backend.domain.dm.dto.request.DmReadRequest;
import com.jipsamoye.backend.domain.dm.dto.request.DmSendRequest;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageEvent;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.service.DmService;
import com.jipsamoye.backend.global.util.WsSessionUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DmWebSocketController {

    private final DmService dmService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/dm/send")
    public void sendMessage(@Valid @Payload DmSendRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = WsSessionUtils.requireUserId(headerAccessor);
        DmMessageResponse response = dmService.sendMessage(
                userId, request.roomId(), request.targetNickname(),
                request.content(), request.imageUrl());

        // draft 전송(roomId == null)도 처리할 수 있도록 서비스가 resolve한 roomId 기준으로 broadcast한다.
        messagingTemplate.convertAndSend("/sub/dm/room/" + response.roomId(),
                DmMessageEvent.of(response, request.clientMessageId()));
    }

    @MessageMapping("/dm/read")
    public void read(@Valid @Payload DmReadRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = WsSessionUtils.requireUserId(headerAccessor);
        dmService.markAsRead(userId, request.roomId());
    }
}
