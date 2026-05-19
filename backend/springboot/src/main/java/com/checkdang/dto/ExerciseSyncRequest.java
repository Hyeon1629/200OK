package com.checkdang.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ExerciseSyncRequest {

    private String sourceId;

    @NotBlank(message = "운동명은 필수입니다.")
    private String exerciseName;

    @NotNull(message = "운동 시간은 필수입니다.")
    private Long duration;

    @NotNull(message = "기록 일시는 필수입니다.")
    private LocalDateTime recordedAt;

    private Integer sets;
    private Integer reps;
    private Double weightKg;
}
