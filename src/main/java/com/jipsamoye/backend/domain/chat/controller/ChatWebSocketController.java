package com.jipsamoye.backend.domain.chat.controller;

import com.jipsamoye.backend.domain.chat.dto.request.ChatSendRequest;
import com.jipsamoye.backend.domain.chat.dto.response.ChatMessageResponse;
import com.jipsamoye.backend.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/send")
    public void sendMessage(ChatSendRequest request) {
        ChatMessageResponse response = chatService.sendMessage(request.userId(), request.content());
        messagingTemplate.convertAndSend("/sub/chat/room", response);
    }

}
