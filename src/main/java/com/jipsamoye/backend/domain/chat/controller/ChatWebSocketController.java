package com.jipsamoye.backend.domain.chat.controller;

import com.jipsamoye.backend.domain.chat.dto.request.ChatSendRequest;
import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.service.ChatService;
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
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/send")
    public void sendMessage(@Valid @Payload ChatSendRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = WsSessionUtils.requireUserId(headerAccessor);
        ChatMessageResponse response = chatService.sendMessage(userId, request.content());
        messagingTemplate.convertAndSend("/sub/chat/room", response);
    }
}
