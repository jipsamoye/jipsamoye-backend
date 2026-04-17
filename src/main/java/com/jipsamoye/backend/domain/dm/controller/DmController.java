package com.jipsamoye.backend.domain.dm.controller;

import com.jipsamoye.backend.domain.dm.dto.response.DmMessageResponse;
import com.jipsamoye.backend.domain.dm.dto.response.DmRoomResponse;
import com.jipsamoye.backend.domain.dm.service.DmService;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "DM", description = "DM API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dm")
public class DmController {

    private final DmService dmService;

    @Operation(summary = "채팅방 목록 조회")
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<DmRoomResponse>>> getRooms(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(dmService.getRooms(userDetails.getUserId())));
    }

    @Operation(summary = "채팅방 생성")
    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<DmRoomResponse>> createRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long targetUserId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(dmService.createRoom(userDetails.getUserId(), targetUserId)));
    }

    @Operation(summary = "메시지 목록 조회")
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<DmMessageResponse>>> getMessages(
            @PathVariable Long roomId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(dmService.getMessages(roomId, userDetails.getUserId(), page, size)));
    }
}
