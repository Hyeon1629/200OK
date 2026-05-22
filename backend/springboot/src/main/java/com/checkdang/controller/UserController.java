package com.checkdang.controller;

import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.UserResponse;
import com.checkdang.service.CognitoService;
import com.checkdang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CognitoService cognitoService;

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Boolean>> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.ok(userService.checkEmailAvailable(email)));
    }

    // 앱이 Cognito에서 받은 ID Token으로 호출 → RDS 사용자 upsert 후 UserResponse 반환
    // 최초 소셜/로컬 로그인 직후 앱이 반드시 호출해야 함 (RDS 사용자 동기화)
    @PostMapping("/social-login")
    public ResponseEntity<ApiResponse<UserResponse>> socialLogin(
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Cognito ID Token이 필요합니다.");
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.syncUserFromCognito(jwt)));
    }

    // 기존 로컬 회원 일괄 Cognito 마이그레이션 (어드민 전용, 1회성)
    // 각 로컬 회원에게 Cognito 비밀번호 재설정 이메일 발송
    @PostMapping("/internal/migrate-to-cognito")
    public ResponseEntity<ApiResponse<Integer>> migrateLocalUsers() {
        int count = userService.migrateLocalUsersToCognito(cognitoService);
        return ResponseEntity.ok(ApiResponse.ok(count));
    }
}
