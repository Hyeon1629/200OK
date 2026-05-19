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
import org.springframework.security.core.userdetails.UserDetails;
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
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PainRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(painRecordService.save(userDetails.getUsername(), request)));
    }

    /**
     * 내 통증 기록 목록 조회
     * - from/to 없으면 전체 조회
     * - from/to 있으면 날짜 범위 조회
     * - bodyArea 있으면 부위 필터
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PainRecordResponse>>> getMyRecords(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) PainRecord.BodyArea bodyArea) {

        List<PainRecordResponse> result;
        if (bodyArea != null) {
            result = painRecordService.getMyRecordsByBodyArea(userDetails.getUsername(), bodyArea);
        } else if (from != null && to != null) {
            result = painRecordService.getMyRecordsByRange(userDetails.getUsername(), from, to);
        } else {
            result = painRecordService.getMyRecords(userDetails.getUsername());
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 통증 기록 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PainRecordResponse>> getById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                painRecordService.getById(userDetails.getUsername(), id)));
    }

    /** 통증 기록 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        painRecordService.delete(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
