package com.jipsamoye.backend.domain.auth.controller;

import com.jipsamoye.backend.domain.auth.service.AuthService;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "둘러보기 (임시 계정 생성)", description = "UUID 기반 임시 유저를 생성하고 세션을 발급합니다.")
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<UserResponse>> createGuest() {
        UserResponse response = authService.createGuest();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "현재 로그인 유저 정보", description = "세션에 저장된 유저 정보를 반환합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @Parameter(description = "유저 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        // TODO: 인증 구현 후 세션에서 userId 추출로 변경
        UserResponse response = authService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "로그아웃", description = "세션을 무효화합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공"));
    }

    @Operation(summary = "회원 탈퇴", description = "유저 계정 및 관련 데이터를 모두 삭제합니다.")
    @DeleteMapping("/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @Parameter(description = "유저 ID (인증 구현 전 임시)") @RequestParam Long userId) {
        // TODO: 인증 구현 후 세션에서 userId 추출로 변경
        authService.withdraw(userId);
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴 성공"));
    }
}
