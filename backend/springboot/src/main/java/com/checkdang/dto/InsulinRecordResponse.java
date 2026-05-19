package com.checkdang.dto;

import com.checkdang.domain.InsulinRecord;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class InsulinRecordResponse {
    private Long id;
    private String insulinType;
    private BigDecimal dosage;
    private LocalDateTime injectedAt;
    private String memo;
    private String userId;
    private LocalDateTime createdAt;

    public static InsulinRecordResponse from(InsulinRecord record) {
        return InsulinRecordResponse.builder()
                .id(record.getId())
                .insulinType(record.getInsulinType())
                .dosage(record.getDosage())
                .injectedAt(record.getInjectedAt())
                .memo(record.getMemo())
                .userId(record.getUserId())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
