package com.checkdang.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoPayApproveRequest {

    private String orderId;  // ready 단계에서 서버가 발급한 주문 ID
    private String pgToken;  // Kakao Pay가 리다이렉트 시 전달하는 결제 토큰
}
