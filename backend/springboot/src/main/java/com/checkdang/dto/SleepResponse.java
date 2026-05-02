package com.checkdang.dto;

import com.checkdang.domain.Sleep;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SleepResponse {

    private Long id;
    private String userId;
    private LocalDateTime sleepTime;
    private LocalDateTime wakeTime;
    private Long duration;
    private Double quality;
    private Sleep.DataSource dataSource;
    private List<SleepStageResponse> stages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SleepResponse from(Sleep sleep) {
        return SleepResponse.builder()
                .id(sleep.getId())
                .userId(sleep.getUserId())
                .sleepTime(sleep.getSleepTime())
                .wakeTime(sleep.getWakeTime())
                .duration(sleep.getDuration())
                .quality(sleep.getQuality())
                .dataSource(sleep.getDataSource())
                .stages(sleep.getStages().stream()
                        .map(SleepStageResponse::from)
                        .toList())
                .createdAt(sleep.getCreatedAt())
                .updatedAt(sleep.getUpdatedAt())
                .build();
    }
}
