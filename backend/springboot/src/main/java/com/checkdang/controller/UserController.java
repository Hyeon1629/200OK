package com.checkdang.controller;

import com.checkdang.dto.LoginRequest;
import com.checkdang.dto.SignupRequest;
import com.checkdang.dto.UserResponse;
import com.checkdang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 소셜 로그인 후 프론트가 Cognito 토큰으로 호출 → DB 유저 생성/조회
    @PostMapping("/social-login")
    public ResponseEntity<UserResponse> socialLogin(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.syncCognitoUser(jwt));
    }

    // 내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getMe(jwt));
    }

    // 기존 이메일/비밀번호 (LOCAL 회원가입)
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }
}
