package com.checkdang.dto;

import lombok.Getter;

@Getter
public class BloodSugarRecordRequest {
    private Integer level;      // 혈당수치 (mg/dL), 필수
    private String measuredAt;  // ISO-8601 (e.g. "2025-05-04T09:30:00"), 필수
    private String memo;        // 선택
    private String mealTiming;  // FASTING / BEFORE_MEAL / AFTER_MEAL / BEDTIME, 선택
}
