package com.jipsamoye.backend.domain.user.controller;

import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.user.dto.request.UserUpdateRequest;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.domain.user.service.UserService;
import com.jipsamoye.backend.global.response.ApiResponse;
import com.jipsamoye.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "유저 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "프로필 조회", description = "닉네임으로 유저 프로필을 조회합니다.")
    @GetMapping("/{nickname}")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            @Parameter(description = "유저 닉네임") @PathVariable String nickname) {
        UserResponse response = userService.getProfile(nickname);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "프로필 수정", description = "본인 프로필을 수정합니다. 변경할 필드만 전송하세요.")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Parameter(description = "유저 ID (인증 구현 전 임시)") @RequestParam Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("프로필 수정 성공", response));
    }

    @Operation(summary = "유저 게시글 목록", description = "특정 유저가 작성한 게시글 목록을 조회합니다.")
    @GetMapping("/{nickname}/posts")
    public ResponseEntity<ApiResponse<PageResponse<PetPostListResponse>>> getUserPosts(
            @Parameter(description = "유저 닉네임") @PathVariable String nickname,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        PageResponse<PetPostListResponse> response = userService.getUserPosts(nickname, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
