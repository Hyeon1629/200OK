package com.checkdang.controller;

import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.PainAnalysisResponse;
import com.checkdang.service.PainAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/pain-analysis")
@RequiredArgsConstructor
public class PainAnalysisController {

    private final PainAnalysisService painAnalysisService;

    /** 통증 기록 1건 + 전날~당일 생활데이터로 AI 원인분석/조치 생성 */
    @PostMapping("/{painRecordId}")
    public ResponseEntity<ApiResponse<PainAnalysisResponse>> analyze(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long painRecordId) {
        return ResponseEntity.ok(ApiResponse.ok(
                painAnalysisService.analyze(jwt.getClaimAsString("email"), painRecordId)));
    }
}
