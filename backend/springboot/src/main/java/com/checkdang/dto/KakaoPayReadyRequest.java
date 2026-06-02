package com.checkdang.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoPayReadyRequest {

    private String subscriptionId;  // "premium_monthly" (수신만, 1개월 단일 확정)
    private Integer premiumMonths;  // 레거시 호환용
}