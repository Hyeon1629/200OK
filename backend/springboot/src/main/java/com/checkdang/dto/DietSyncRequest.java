package com.checkdang.dto;

import com.checkdang.domain.Diet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class DietSyncRequest {

    private String sourceId;

    @NotBlank(message = "음식명은 필수입니다.")
    private String foodName;

    @NotNull(message = "식사 유형은 필수입니다.")
    private Diet.MealType mealType;

    @NotNull(message = "식사 기록 일시는 필수입니다.")
    private LocalDateTime recordedAt;

    private Double calories;
    private Double carbohydrate;
    private Double protein;
    private Double totalFat;
    private Double sugar;
    private Double dietaryFiber;
    private Double sodium;
}
