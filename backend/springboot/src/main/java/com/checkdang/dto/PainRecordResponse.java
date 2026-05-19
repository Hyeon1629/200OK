package com.checkdang.dto;

import com.checkdang.domain.PainRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PainRecordResponse {

    private Long id;
    private String userId;
    private Integer intensity;
    private PainRecord.BodyArea bodyArea;
    private PainRecord.BodySide bodySide;
    private Double bodyMapX;
    private Double bodyMapY;
    private PainRecord.PainType painType;
    private String memo;
    private LocalDateTime recordedAt;
    private LocalDateTime createdAt;

    public static PainRecordResponse from(PainRecord record) {
        return PainRecordResponse.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .intensity(record.getIntensity())
                .bodyArea(record.getBodyArea())
                .bodySide(record.getBodySide())
                .bodyMapX(record.getBodyMapX())
                .bodyMapY(record.getBodyMapY())
                .painType(record.getPainType())
                .memo(record.getMemo())
                .recordedAt(record.getRecordedAt())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
