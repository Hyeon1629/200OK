package com.checkdang.service;

import com.checkdang.domain.PaymentRecord;
import com.checkdang.domain.User;
import com.checkdang.dto.GooglePlayRtdnRequest;
import com.checkdang.dto.GooglePlayVerifyRequest;
import com.checkdang.dto.GooglePlayVerifyResponse;
import com.checkdang.repository.PaymentRecordRepository;
import com.checkdang.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlayBillingService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${google.play.package-name}")
    private String packageName;

    @Value("${google.play.service-account-key}")
    private String serviceAccountKey;

    @Value("${google.play.subscription-id}")
    private String defaultSubscriptionId;

    @Value("${google.play.mock-mode:false}")
    private boolean mockMode;
    // Play Console API 액세스 미설정 상태에서 시연용.
    // true면 AndroidPublisher API 호출 없이 isPremium: true를 바로 반환 (DB 미반영).

    // notificationType 상수
    private static final int TYPE_RENEWED   = 2;
    private static final int TYPE_CANCELED  = 3;
    private static final int TYPE_PURCHASED = 4;
    private static final int TYPE_REVOKED   = 12;
    private static final int TYPE_EXPIRED   = 13;

    // Android 앱이 구매 완료 후 호출 — purchaseToken 검증 후 프리미엄 부여
    public GooglePlayVerifyResponse verify(String userEmail, GooglePlayVerifyRequest request) {
        if (mockMode) {
            log.info("[MOCK] Google Play 구독 검증 (DB 미반영) - purchaseToken: {}", request.getPurchaseToken());
            return GooglePlayVerifyResponse.builder()
                    .isPremium(true)
                    .premiumExpiresAt(null)
                    .build();
        }

        if (request.getPurchaseToken() == null || request.getPurchaseToken().isBlank()) {
            throw new IllegalArgumentException("purchaseToken은 필수입니다.");
        }

        String subscriptionId = (request.getSubscriptionId() != null && !request.getSubscriptionId().isBlank())
                ? request.getSubscriptionId()
                : defaultSubscriptionId;

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        SubscriptionPurchase purchase = fetchSubscription(subscriptionId, request.getPurchaseToken());
        validatePaymentState(purchase);

        Instant premiumExpiresAt = Instant.ofEpochMilli(purchase.getExpiryTimeMillis());
        String now = Instant.now().toString();

        paymentRecordRepository.save(PaymentRecord.builder()
                .userId(String.valueOf(user.getId()))
                .orderId(request.getPurchaseToken()) // Google은 별도 orderId 없음, purchaseToken을 SK로 사용
                .paymentMethod(PaymentRecord.PaymentMethod.GOOGLE_PLAY.name())
                .tid(purchase.getOrderId())          // Google 내부 주문번호
                .itemName("체크당 프리미엄")
                .amount(0)                           // Google Play는 결제금액을 API로 제공하지 않음
                .status(PaymentRecord.PaymentStatus.APPROVED.name())
                .premiumMonths(1)
                .approvedAt(now)
                .createdAt(now)
                .build());

        activatePremium(user, premiumExpiresAt);
        acknowledgeIfNeeded(subscriptionId, request.getPurchaseToken(), purchase);

        return GooglePlayVerifyResponse.builder()
                .isPremium(true)
                .orderId(purchase.getOrderId())
                .subscriptionId(subscriptionId)
                .premiumMonths(1)
                .premiumExpiresAt(premiumExpiresAt)
                .verifiedAt(now)
                .build();
    }

    // Pub/Sub RTDN 수신 — 구독 상태 변화에 따라 프리미엄 갱신
    public void handleRtdn(GooglePlayRtdnRequest rtdn) {
        if (rtdn.getMessage() == null || rtdn.getMessage().getData() == null) {
            log.warn("RTDN 메시지 데이터 없음, 무시");
            return;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(rtdn.getMessage().getData()));
            JsonNode root = objectMapper.readTree(decoded);

            JsonNode notifNode = root.path("subscriptionNotification");
            if (notifNode.isMissingNode()) {
                log.debug("subscriptionNotification 없는 RTDN (테스트 알림 등), 무시");
                return;
            }

            int notificationType = notifNode.path("notificationType").asInt();
            String purchaseToken  = notifNode.path("purchaseToken").asText();
            String subscriptionId = notifNode.path("subscriptionId").asText(defaultSubscriptionId);

            log.info("RTDN 수신 — type: {}, subscriptionId: {}", notificationType, subscriptionId);

            switch (notificationType) {
                case TYPE_PURCHASED, TYPE_RENEWED -> handleActivation(purchaseToken, subscriptionId);
                case TYPE_REVOKED, TYPE_EXPIRED   -> handleDeactivation(purchaseToken);
                case TYPE_CANCELED -> log.info("구독 취소 예약 (만료 전까지 프리미엄 유지)");
                default -> log.debug("처리하지 않는 RTDN type: {}", notificationType);
            }
        } catch (Exception e) {
            // Pub/Sub은 200 응답을 받아야 재전송하지 않음 — 예외를 삼키고 로그만
            log.error("RTDN 처리 실패: {}", e.getMessage(), e);
        }
    }

    private void handleActivation(String purchaseToken, String subscriptionId) throws Exception {
        SubscriptionPurchase purchase = fetchSubscription(subscriptionId, purchaseToken);
        if (purchase.getPaymentState() == null || purchase.getPaymentState() != 1) {
            log.warn("결제 미완료 상태 RTDN, 무시 — paymentState: {}", purchase.getPaymentState());
            return;
        }

        Instant premiumExpiresAt = Instant.ofEpochMilli(purchase.getExpiryTimeMillis());

        // orderId(=purchaseToken) scan으로 userId를 역조회 → User 프리미엄 갱신
        paymentRecordRepository.findByOrderId(purchaseToken)
                .ifPresentOrElse(
                        record -> userRepository.findById(Long.parseLong(record.getUserId()))
                                .ifPresent(user -> activatePremium(user, premiumExpiresAt)),
                        () -> log.warn("RTDN activation: purchaseToken에 해당하는 PaymentRecord 없음 — {}", purchaseToken)
                );
    }

    private void handleDeactivation(String purchaseToken) {
        // orderId(=purchaseToken) scan으로 userId를 역조회 → User 프리미엄 해제
        paymentRecordRepository.findByOrderId(purchaseToken)
                .ifPresentOrElse(
                        record -> userRepository.findById(Long.parseLong(record.getUserId()))
                                .ifPresent(this::deactivatePremium),
                        () -> log.warn("RTDN deactivation: purchaseToken에 해당하는 PaymentRecord 없음 — {}", purchaseToken)
                );
    }

    private void activatePremium(User user, Instant premiumExpiresAt) {
        // 현재 프리미엄이 남아있으면 그 이후로 연장, 아니면 지금부터 계산
        Instant base = (user.getPremiumExpiresAt() != null && user.getPremiumExpiresAt().isAfter(Instant.now()))
                ? user.getPremiumExpiresAt()
                : Instant.now();
        Instant finalExpiry = premiumExpiresAt.isAfter(base) ? premiumExpiresAt : base;

        user.setIsPremium(true);
        user.setPremiumExpiresAt(finalExpiry);
        userRepository.save(user);
        log.info("프리미엄 활성화 — userId: {}, 만료: {}", user.getId(), finalExpiry);
    }

    private void deactivatePremium(User user) {
        user.setIsPremium(false);
        user.setPremiumExpiresAt(null);
        userRepository.save(user);
        log.info("프리미엄 비활성화 — userId: {}", user.getId());
    }

    // 구매 확인(Acknowledge) — 미확인 상태면 Google이 3일 후 자동 환불
    // acknowledgementState: 0=미확인, 1=확인완료
    private void acknowledgeIfNeeded(String subscriptionId, String purchaseToken,
                                     SubscriptionPurchase purchase) {
        if (purchase.getAcknowledgementState() != null && purchase.getAcknowledgementState() == 1) {
            return; // 이미 확인 완료
        }
        try {
            buildPublisher().purchases().subscriptions()
                    .acknowledge(packageName, subscriptionId, purchaseToken,
                            new SubscriptionPurchasesAcknowledgeRequest())
                    .execute();
            log.info("구매 확인(Acknowledge) 완료 — purchaseToken: {}", purchaseToken);
        } catch (Exception e) {
            // Acknowledge 실패는 치명적이지 않음 — 이미 프리미엄 부여 완료 상태
            // Google이 재시도하거나 RTDN으로 다시 알려줌
            log.error("구매 확인(Acknowledge) 실패: {}", e.getMessage());
        }
    }

    private SubscriptionPurchase fetchSubscription(String subscriptionId, String purchaseToken) {
        try {
            AndroidPublisher publisher = buildPublisher();
            return publisher.purchases()
                    .subscriptions()
                    .get(packageName, subscriptionId, purchaseToken)
                    .execute();
        } catch (Exception e) {
            log.error("Google Play API 호출 실패: {}", e.getMessage());
            throw new IllegalArgumentException("Google Play 구독 검증에 실패했습니다.");
        }
    }

    private void validatePaymentState(SubscriptionPurchase purchase) {
        // paymentState: 0=대기, 1=결제완료, 2=무료체험, null=취소
        Integer paymentState = purchase.getPaymentState();
        if (paymentState == null || (paymentState != 1 && paymentState != 2)) {
            throw new IllegalArgumentException("유효하지 않은 구독 상태입니다.");
        }
        if (purchase.getExpiryTimeMillis() == null ||
                Instant.ofEpochMilli(purchase.getExpiryTimeMillis()).isBefore(Instant.now())) {
            throw new IllegalArgumentException("이미 만료된 구독입니다.");
        }
    }

    private AndroidPublisher buildPublisher() throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(serviceAccountKey);
        GoogleCredentials credentials = ServiceAccountCredentials
                .fromStream(new ByteArrayInputStream(keyBytes))
                .createScoped(List.of("https://www.googleapis.com/auth/androidpublisher"));

        return new AndroidPublisher.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("checkdang")
                .build();
    }
}
