package com.checkdang.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoPayReadyRequest {

    private Integer premiumMonths; // 1 또는 12 (미입력 시 기본 1개월)
}
