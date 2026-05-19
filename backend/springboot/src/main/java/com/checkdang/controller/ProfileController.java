package com.checkdang.controller;

import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.UserProfileUpdateRequest;
import com.checkdang.dto.UserResponse;
import com.checkdang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    /** 내 프로필 조회 */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userDetails.getUsername())));
    }

    /** 내 프로필 수정 — null 필드는 기존 값 유지 (Partial Update) */
    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.updateProfile(userDetails.getUsername(), request)));
    }
}
