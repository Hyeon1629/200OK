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

    @NotBlank(message = "음식명은 필수입니다.")
    private String foodName;

    @NotNull(message = "식사 유형은 필수입니다.")
    private Diet.MealType mealType;

    @NotNull(message = "식사 시각은 필수입니다.")
    private LocalDateTime mealTime;

    private Double calories;
    private Double carbohydrate;
    private Double protein;
    private Double totalFat;
    private Double sugar;
    private Double dietaryFiber;
    private Double sodium;
}