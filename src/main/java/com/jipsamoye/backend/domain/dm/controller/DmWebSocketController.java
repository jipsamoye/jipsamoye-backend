package com.jipsamoye.backend.domain.dm.controller;

import com.jipsamoye.backend.domain.dm.dto.request.DmSendRequest;
import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.service.DmService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DmWebSocketController {

    private final DmService dmService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/dm/send")
    public void sendMessage(DmSendRequest request) {
        DmMessageResponse response = dmService.sendMessage(
                request.getUserId(), request.getRoomId(),
                request.getContent(), request.getImageUrl());

        messagingTemplate.convertAndSend("/sub/dm/room/" + request.getRoomId(), response);
    }
}
