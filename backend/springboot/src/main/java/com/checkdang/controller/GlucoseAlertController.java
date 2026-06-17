package com.checkdang.controller;

import com.checkdang.dto.GlucoseAlertRequest;
import com.checkdang.service.GlucoseAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// FastAPI(같은 내부망)가 혈당 저장 시점에 호출하는 서비스 간 전용 엔드포인트.
// Cognito JWT가 없으므로 SecurityConfig에서 permitAll, 대신 공유 키로 검증.
@RestController
@RequestMapping("/api/internal/glucose-alerts")
@RequiredArgsConstructor
public class GlucoseAlertController {

    private final GlucoseAlertService glucoseAlertService;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @PostMapping
    public ResponseEntity<Void> receiveAlert(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @RequestBody GlucoseAlertRequest request) {
        if (!internalApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "유효하지 않은 내부 API 키입니다.");
        }
        glucoseAlertService.handleAlert(request);
        return ResponseEntity.ok().build();
    }
}
