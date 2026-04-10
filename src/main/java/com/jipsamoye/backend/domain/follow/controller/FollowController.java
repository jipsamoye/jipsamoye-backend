package com.jipsamoye.backend.domain.follow.controller;

import com.jipsamoye.backend.domain.follow.dto.response.FollowUserResponse;
import com.jipsamoye.backend.domain.follow.service.FollowService;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Follow", description = "팔로우 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "팔로우 토글", description = "팔로우가 없으면 추가, 있으면 취소합니다. 자기 자신은 팔로우 불가.")
    @PostMapping("/{nickname}/follow")
    public ResponseEntity<ApiResponse<Boolean>> toggleFollow(
            @Parameter(description = "팔로우할 유저 닉네임") @PathVariable String nickname,
            @Parameter(description = "유저 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        boolean followed = followService.toggleFollow(nickname, userId);
        String message = followed ? "팔로우 성공" : "언팔로우 성공";
        return ResponseEntity.ok(ApiResponse.success(message, followed));
    }

    @Operation(summary = "팔로워 목록", description = "해당 유저를 팔로우하는 유저 목록을 조회합니다.")
    @GetMapping("/{nickname}/followers")
    public ResponseEntity<ApiResponse<PageResponse<FollowUserResponse>>> getFollowers(
            @Parameter(description = "유저 닉네임") @PathVariable String nickname,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<FollowUserResponse> response = followService.getFollowers(nickname, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "팔로잉 목록", description = "해당 유저가 팔로우하는 유저 목록을 조회합니다.")
    @GetMapping("/{nickname}/following")
    public ResponseEntity<ApiResponse<PageResponse<FollowUserResponse>>> getFollowing(
            @Parameter(description = "유저 닉네임") @PathVariable String nickname,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<FollowUserResponse> response = followService.getFollowing(nickname, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
