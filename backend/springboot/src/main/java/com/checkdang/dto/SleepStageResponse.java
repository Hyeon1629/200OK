package com.checkdang.dto;

import com.checkdang.domain.SleepStage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SleepStageResponse {

    private Long id;
    private SleepStage.StageType stageType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMinutes;

    public static SleepStageResponse from(SleepStage stage) {
        return SleepStageResponse.builder()
                .id(stage.getId())
                .stageType(stage.getStageType())
                .startTime(stage.getStartTime())
                .endTime(stage.getEndTime())
                .durationMinutes(stage.getDurationMinutes())
                .build();
    }
}
