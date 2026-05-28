package com.checkdang.controller;

import com.checkdang.domain.PainRecord;
import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.PainRecordRequest;
import com.checkdang.dto.PainRecordResponse;
import com.checkdang.service.PainRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/pain-records")
@RequiredArgsConstructor
public class PainRecordController {

    private final PainRecordService painRecordService;

    /** 통증 기록 저장 */
    @PostMapping
    public ResponseEntity<ApiResponse<PainRecordResponse>> save(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PainRecordRequest request) {
        PainRecordResponse response = painRecordService.save(jwt.getClaimAsString("email"), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /** 내 통증 기록 목록 (bodyPart 필터 / from+to 범위 / 전체) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PainRecordResponse>>> getMyRecords(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) PainRecord.BodyPart bodyPart) {
        String email = jwt.getClaimAsString("email");
        List<PainRecordResponse> result;
        if (bodyPart != null) {
            result = painRecordService.getMyRecordsByBodyPart(email, bodyPart);
        } else if (from != null && to != null) {
            result = painRecordService.getMyRecordsByRange(email, from, to);
        } else {
            result = painRecordService.getMyRecords(email);
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PainRecordResponse>> getById(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(painRecordService.getById(jwt.getClaimAsString("email"), id)));
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        painRecordService.delete(jwt.getClaimAsString("email"), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
