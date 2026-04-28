package com.checkdang.controller;

import com.checkdang.common.ApiResponse;
import com.checkdang.dto.LoginRequest;
import com.checkdang.dto.SignupRequest;
import com.checkdang.dto.UpdateProfileRequest;
import com.checkdang.dto.UserResponse;
import com.checkdang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 소셜 로그인 후 프론트가 Cognito 토큰으로 호출 → DB 유저 생성/조회
    @PostMapping("/api/auth/social-login")
    public ResponseEntity<ApiResponse<UserResponse>> socialLogin(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(userService.syncCognitoUser(jwt)));
    }

    // 기존 이메일/비밀번호 (LOCAL 회원가입)
    @PostMapping("/api/auth/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(userService.signup(request)));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.login(request)));
    }

    @GetMapping("/api/users/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(jwt)));
    }

    @PatchMapping("/api/users/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("프로필이 수정되었습니다.", userService.updateProfile(jwt, request)));
    }

    @DeleteMapping("/api/users/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        userService.deleteAccount(jwt);
        return ResponseEntity.ok(ApiResponse.ok("회원 탈퇴가 완료되었습니다.", null));
    }
}
