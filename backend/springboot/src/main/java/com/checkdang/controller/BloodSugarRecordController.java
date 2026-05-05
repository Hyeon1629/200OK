package com.checkdang.controller;

import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.BloodSugarRecordRequest;
import com.checkdang.dto.BloodSugarRecordResponse;
import com.checkdang.service.BloodSugarRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/records/blood-sugar")
@RequiredArgsConstructor
public class BloodSugarRecordController {

    private final BloodSugarRecordService bloodSugarRecordService;

    @PostMapping
    public ResponseEntity<ApiResponse<BloodSugarRecordResponse>> save(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody BloodSugarRecordRequest request) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("로그인이 필요합니다."));
        }

        BloodSugarRecordResponse response = bloodSugarRecordService.save(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
