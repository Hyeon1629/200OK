package com.checkdang.controller;

import com.checkdang.domain.PaymentRecord;
import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.GooglePlayRtdnRequest;
import com.checkdang.dto.GooglePlayVerifyRequest;
import com.checkdang.dto.GooglePlayVerifyResponse;
import com.checkdang.dto.KakaoPayApproveRequest;
import com.checkdang.dto.KakaoPayApproveResponse;
import com.checkdang.dto.KakaoPayReadyRequest;
import com.checkdang.dto.KakaoPayReadyResponse;
import com.checkdang.service.GooglePlayBillingService;
import com.checkdang.service.KakaoPayService;
import com.checkdang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import java.util.List;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final KakaoPayService kakaoPayService;
    private final GooglePlayBillingService googlePlayBillingService;
    private final UserService userService;

    // 결제 준비: 카카오 결제 페이지 URL 발급
    @PostMapping("/kakao/ready")
    public ResponseEntity<ApiResponse<KakaoPayReadyResponse>> kakaoReady(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody KakaoPayReadyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                kakaoPayService.ready(userService.resolveEmail(jwt), request)));
    }

    // 결제 승인: 카카오 결제 완료 후 최종 확정
    @PostMapping("/kakao/approve")
    public ResponseEntity<ApiResponse<KakaoPayApproveResponse>> kakaoApprove(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody KakaoPayApproveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                kakaoPayService.approve(userService.resolveEmail(jwt), request)));
    }

    // 카카오페이 → 백엔드 콜백 → 앱 딥링크 redirect
    @GetMapping("/kakao/callback/success")
    public ResponseEntity<Void> kakaoCallbackSuccess(@RequestParam("pg_token") String pgToken) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("checkdang://payment/success?pg_token=" + pgToken))
                .build();
    }

    @GetMapping("/kakao/callback/cancel")
    public ResponseEntity<Void> kakaoCallbackCancel() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("checkdang://payment/cancel"))
                .build();
    }

    @GetMapping("/kakao/callback/fail")
    public ResponseEntity<Void> kakaoCallbackFail() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("checkdang://payment/fail"))
                .build();
    }

    // 결제 이력 조회
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PaymentRecord>>> getHistory(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(
                kakaoPayService.getHistory(userService.resolveEmail(jwt))));
    }

    // Google Play: Android 앱이 구매 완료 후 purchaseToken 전송 → 검증 후 프리미엄 부여
    @PostMapping("/google/verify")
    public ResponseEntity<ApiResponse<GooglePlayVerifyResponse>> googleVerify(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody GooglePlayVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                googlePlayBillingService.verify(userService.resolveEmail(jwt), request)));
    }

    // Google Play: Pub/Sub RTDN 수신 — 구독 갱신/취소/만료 등 상태 변화 처리 (JWT 불필요)
    @PostMapping("/google/rtdn")
    public ResponseEntity<Void> googleRtdn(@RequestBody GooglePlayRtdnRequest request) {
        googlePlayBillingService.handleRtdn(request);
        return ResponseEntity.ok().build(); // 200 반환 필수 — 아니면 Pub/Sub이 재전송
    }
}