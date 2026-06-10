package com.jipsamoye.backend.domain.auth.controller;

import com.jipsamoye.backend.domain.auth.dto.request.NaverLoginRequest;
import com.jipsamoye.backend.domain.auth.dto.response.NaverLoginResponse;
import com.jipsamoye.backend.domain.auth.service.AuthService;
import com.jipsamoye.backend.domain.user.dto.response.UserResponse;
import com.jipsamoye.backend.global.config.security.CustomUserDetails;
import com.jipsamoye.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "네이버 소셜 로그인",
            description = "네이버 인가 코드(code, state)를 받아 토큰 교환 및 프로필 조회 후 세션을 발급합니다. " +
                    "state CSRF 검증 책임은 프론트엔드에 있으며 백엔드는 state를 토큰 요청에 전달만 합니다."
    )
    @PostMapping("/naver/login")
    public ResponseEntity<ApiResponse<NaverLoginResponse>> naverLogin(
            @Valid @RequestBody NaverLoginRequest request) {
        NaverLoginResponse response = authService.naverLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "둘러보기 (임시 계정 생성)", description = "UUID 기반 임시 유저를 생성하고 세션을 발급합니다.")
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<UserResponse>> createGuest() {
        UserResponse response = authService.createGuest();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "현재 로그인 유저 정보", description = "세션에 저장된 유저 정보를 반환합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = authService.getMe(userDetails.getUserId());
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
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.withdraw(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴 성공"));
    }
}
