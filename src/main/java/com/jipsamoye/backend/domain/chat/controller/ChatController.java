package com.jipsamoye.backend.domain.chat.controller;

import com.jipsamoye.backend.domain.chat.dto.response.ChatMessagesResponse;
import com.jipsamoye.backend.domain.chat.service.ChatService;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chat", description = "오픈채팅 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "메시지 조회 (커서 기반)")
    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<ChatMessagesResponse>> getMessages(
            @Parameter(description = "조회할 메시지 수") @RequestParam(defaultValue = "30") int size,
            @Parameter(description = "이 ID 이전 메시지 조회 (미입력 시 최신)") @RequestParam(required = false) Long beforeId) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getMessages(size, beforeId)));
    }
}
