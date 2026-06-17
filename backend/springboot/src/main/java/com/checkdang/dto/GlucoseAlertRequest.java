package com.checkdang.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GlucoseAlertRequest {
    private String userId;
    private Integer level;
    private String alertType;     // LOW / HIGH
    private String measuredAt;    // ISO-8601
    private String mealTiming;
}
