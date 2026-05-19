package com.checkdang.dto;

import com.checkdang.domain.Exercise;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ExerciseResponse {

    private Long id;
    private String userId;
    private String sourceId;
    private String exerciseName;
    private Long duration;
    private LocalDateTime recordedAt;
    private Integer sets;
    private Integer reps;
    private Double weightKg;
    private String aiResult;
    private String aiRecommendation;
    private Exercise.DataSource dataSource;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExerciseResponse from(Exercise exercise) {
        return ExerciseResponse.builder()
                .id(exercise.getId())
                .userId(exercise.getUserId())
                .sourceId(exercise.getSourceId())
                .exerciseName(exercise.getExerciseName())
                .duration(exercise.getDuration())
                .recordedAt(exercise.getRecordedAt())
                .sets(exercise.getSets())
                .reps(exercise.getReps())
                .weightKg(exercise.getWeightKg())
                .aiResult(exercise.getAiResult())
                .aiRecommendation(exercise.getAiRecommendation())
                .dataSource(exercise.getDataSource())
                .createdAt(exercise.getCreatedAt())
                .updatedAt(exercise.getUpdatedAt())
                .build();
    }
}
