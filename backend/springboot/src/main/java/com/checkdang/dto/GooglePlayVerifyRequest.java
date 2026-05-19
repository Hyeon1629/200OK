package com.checkdang.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GooglePlayVerifyRequest {

    private String purchaseToken;
    private String subscriptionId; // null이면 application.yaml 기본값 사용
}
