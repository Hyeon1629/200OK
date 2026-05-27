package com.checkdang.controller;

import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.UserProfileUpdateRequest;
import com.checkdang.dto.UserResponse;
import com.checkdang.service.UserService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    /** GET /api/user/profile — 토큰으로 내 프로필 조회 */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userDetails.getUsername())));
    }

    /** PUT /api/user/profile — 닉네임/생년월일/성별/키/몸무게 수정 */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.updateProfile(userDetails.getUsername(), request)));
    }

    /**
     * PUT /api/user/fcm-token
     * 앱 실행 시 또는 토큰 갱신 시 FCM 토큰을 서버에 등록.
     * 가족 알림 수신을 위해 필수.
     */
    @PutMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody FcmTokenRequest request) {
        userService.updateFcmToken(userDetails.getUsername(), request.getFcmToken());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Getter
    @NoArgsConstructor
    static class FcmTokenRequest {
        private String fcmToken;
    }
}
