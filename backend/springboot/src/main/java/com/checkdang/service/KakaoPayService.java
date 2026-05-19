package com.checkdang.service;

import com.checkdang.domain.PaymentRecord;
import com.checkdang.domain.User;
import com.checkdang.dto.KakaoPayApproveRequest;
import com.checkdang.dto.KakaoPayApproveResponse;
import com.checkdang.dto.KakaoPayReadyRequest;
import com.checkdang.dto.KakaoPayReadyResponse;
import com.checkdang.repository.PaymentRecordRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${kakao.pay.secret-key}")
    private String secretKey;

    @Value("${kakao.pay.cid}")
    private String cid;

    @Value("${kakao.pay.approval-url}")
    private String approvalUrl;

    @Value("${kakao.pay.cancel-url}")
    private String cancelUrl;

    @Value("${kakao.pay.fail-url}")
    private String failUrl;

    @Value("${kakao.pay.premium-price}")
    private Integer premiumPrice;

    private static final String KAKAO_READY_URL = "https://open-api.kakaopay.com/online/v1/payment/ready";
    private static final String KAKAO_APPROVE_URL = "https://open-api.kakaopay.com/online/v1/payment/approve";

    public List<PaymentRecord> getHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return paymentRecordRepository.findByUserIdOrderByCreatedAtDesc(String.valueOf(user.getId()));
    }

    public KakaoPayReadyResponse ready(String userEmail, KakaoPayReadyRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        int months = (request.getPremiumMonths() != null && request.getPremiumMonths() > 0)
                ? request.getPremiumMonths() : 1;
        int amount = premiumPrice * months;
        String orderId = UUID.randomUUID().toString();
        String itemName = "체크당 프리미엄 " + months + "개월";

        Map<String, Object> body = new HashMap<>();
        body.put("cid", cid);
        body.put("partner_order_id", orderId);
        body.put("partner_user_id", String.valueOf(user.getId()));
        body.put("item_name", itemName);
        body.put("quantity", 1);
        body.put("total_amount", amount);
        body.put("tax_free_amount", 0);
        body.put("approval_url", approvalUrl);
        body.put("cancel_url", cancelUrl);
        body.put("fail_url", failUrl);

        Map<String, Object> kakaoResponse = callKakaoApi(KAKAO_READY_URL, body, "결제 준비");
        String tid = (String) kakaoResponse.get("tid");

        paymentRecordRepository.save(PaymentRecord.builder()
                .userId(String.valueOf(user.getId()))
                .paymentMethod(PaymentRecord.PaymentMethod.KAKAO_PAY.name())
                .tid(tid)
                .orderId(orderId)
                .itemName(itemName)
                .amount(amount)
                .status(PaymentRecord.PaymentStatus.READY.name())
                .premiumMonths(months)
                .createdAt(Instant.now().toString())
                .build());

        return KakaoPayReadyResponse.builder()
                .orderId(orderId)
                .nextRedirectAppUrl((String) kakaoResponse.get("next_redirect_app_url"))
                .nextRedirectMobileUrl((String) kakaoResponse.get("next_redirect_mobile_url"))
                .nextRedirectPcUrl((String) kakaoResponse.get("next_redirect_pc_url"))
                .build();
    }

    public KakaoPayApproveResponse approve(String userEmail, KakaoPayApproveRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        PaymentRecord record = paymentRecordRepository
                .findByUserIdAndOrderIdAndStatus(
                        String.valueOf(user.getId()), request.getOrderId(), PaymentRecord.PaymentStatus.READY)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 주문입니다."));

        Map<String, Object> body = new HashMap<>();
        body.put("cid", cid);
        body.put("tid", record.getTid());
        body.put("partner_order_id", record.getOrderId());
        body.put("partner_user_id", String.valueOf(user.getId()));
        body.put("pg_token", request.getPgToken());

        // 예외 발생 시 DynamoDB putItem은 롤백 없음 → READY 상태 유지 (재시도 가능)
        callKakaoApi(KAKAO_APPROVE_URL, body, "결제 승인");

        String approvedAt = Instant.now().toString();
        record.setStatus(PaymentRecord.PaymentStatus.APPROVED.name());
        record.setApprovedAt(approvedAt);
        paymentRecordRepository.save(record);

        Instant baseTime = (user.getPremiumExpiresAt() != null && user.getPremiumExpiresAt().isAfter(Instant.now()))
                ? user.getPremiumExpiresAt()
                : Instant.now();
        Instant premiumExpiresAt = baseTime.plus(record.getPremiumMonths() * 30L, ChronoUnit.DAYS);

        user.setIsPremium(true);
        user.setPremiumExpiresAt(premiumExpiresAt);
        userRepository.save(user);

        return KakaoPayApproveResponse.builder()
                .orderId(record.getOrderId())
                .itemName(record.getItemName())
                .amount(record.getAmount())
                .premiumMonths(record.getPremiumMonths())
                .premiumExpiresAt(premiumExpiresAt)
                .approvedAt(approvedAt)
                .build();
    }

    private Map<String, Object> callKakaoApi(String url, Map<String, Object> body, String action) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {});

            if (response.getBody() == null) {
                throw new IllegalArgumentException("카카오페이 " + action + "에 실패했습니다.");
            }
            return response.getBody();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("카카오페이 {} 실패 - status: {}, body: {}", action, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalArgumentException("카카오페이 " + action + "에 실패했습니다.");
        } catch (Exception e) {
            log.error("카카오페이 {} 실패 - {}", action, e.getMessage());
            throw new IllegalArgumentException("카카오페이 " + action + "에 실패했습니다.");
        }
    }
}
