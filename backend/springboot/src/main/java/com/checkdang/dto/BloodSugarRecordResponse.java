package com.checkdang.dto;

import com.checkdang.domain.BloodSugarRecord;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BloodSugarRecordResponse {
    private String userDate;
    private String timestamp;
    private Integer level;
    private String memo;
    private String mealTiming;

    public static BloodSugarRecordResponse from(BloodSugarRecord record) {
        return BloodSugarRecordResponse.builder()
                .userDate(record.getUserDate())
                .timestamp(record.getTimestamp())
                .level(record.getLevel())
                .memo(record.getMemo())
                .mealTiming(record.getMealTiming())
                .build();
    }
}
