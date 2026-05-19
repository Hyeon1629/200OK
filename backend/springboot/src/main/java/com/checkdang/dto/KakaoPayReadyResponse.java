package com.checkdang.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoPayReadyResponse {

    private String orderId;
    private String nextRedirectAppUrl;    // 안드로이드 앱용 결제 URL
    private String nextRedirectMobileUrl; // 모바일 웹용 결제 URL
    private String nextRedirectPcUrl;     // PC 웹용 결제 URL
}
