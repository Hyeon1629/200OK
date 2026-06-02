package com.checkdang.controller;

import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.InsulinRecordRequest;
import com.checkdang.dto.InsulinRecordResponse;
import com.checkdang.service.InsulinRecordService;
import com.checkdang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/records/insulin")
@RequiredArgsConstructor
public class InsulinRecordController {

    private final InsulinRecordService insulinRecordService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<InsulinRecordResponse>> save(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody InsulinRecordRequest request) {

        InsulinRecordResponse response = insulinRecordService.save(userService.resolveEmail(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
