package com.checkdang.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecord {

    private String userId;        // PK
    private String orderId;       // SK (UUID)
    private String paymentMethod; // PaymentMethod enum → String 저장
    private String tid;
    private String itemName;
    private Integer amount;
    private String status;        // PaymentStatus enum → String 저장
    private Integer premiumMonths;
    private String approvedAt;    // ISO-8601, 승인 전 null
    private String createdAt;     // ISO-8601

    public enum PaymentMethod {
        KAKAO_PAY, GOOGLE_PLAY
    }

    public enum PaymentStatus {
        READY, APPROVED, CANCELLED, FAILED
    }
}
