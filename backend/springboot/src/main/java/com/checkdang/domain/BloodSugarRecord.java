package com.checkdang.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloodSugarRecord {
    private String userDate;   // PK: "{userId}#{YYYY-MM-DD}"
    private String timestamp;  // SK: ISO-8601 (e.g. "2025-05-04T09:30:00")
    private Integer level;     // 혈당수치 (mg/dL)
    private String memo;
    private String mealTiming; // FASTING / BEFORE_MEAL / AFTER_MEAL / BEDTIME
}
