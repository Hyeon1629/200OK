package com.checkdang.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class GooglePlayVerifyResponse {

    private String orderId;
    private String subscriptionId;
    private Integer premiumMonths;
    private Instant premiumExpiresAt;
    private String verifiedAt;      // ISO-8601
}
