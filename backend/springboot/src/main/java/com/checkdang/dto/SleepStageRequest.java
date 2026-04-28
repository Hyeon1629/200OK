package com.checkdang.dto;

import com.checkdang.domain.SleepStage;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class SleepStageRequest {

    @NotNull(message = "수면 단계 유형은 필수입니다.")
    private SleepStage.StageType stageType;

    @NotNull(message = "단계 시작 시각은 필수입니다.")
    private LocalDateTime startTime;

    @NotNull(message = "단계 종료 시각은 필수입니다.")
    private LocalDateTime endTime;

    @NotNull(message = "단계 지속 시간은 필수입니다.")
    private Long durationMinutes;
}
