package com.checkdang.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class InsulinRecordRequest {
    private String insulinType;  // 인슐린 타입, 필수
    private BigDecimal dosage;   // 투여량, 필수
    private String injectedAt;   // ISO-8601 (e.g. "2025-05-04T09:30:00"), 필수
    private String memo;         // 특이사항, 선택
}
