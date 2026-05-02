package com.checkdang.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class SleepSyncRequest {

    @NotNull(message = "수면 시작 시각은 필수입니다.")
    private LocalDateTime sleepTime;

    @NotNull(message = "기상 시각은 필수입니다.")
    private LocalDateTime wakeTime;

    @NotNull(message = "총 수면 시간은 필수입니다.")
    private Long duration;

    private Double quality;

    @NotNull(message = "수면 단계 목록은 필수입니다.")
    @Valid
    private List<SleepStageRequest> stages;
}
