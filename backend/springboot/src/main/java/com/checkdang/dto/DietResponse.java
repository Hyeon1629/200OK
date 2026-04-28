package com.checkdang.dto;

import com.checkdang.domain.Diet;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DietResponse {

    private Long id;
    private String userId;
    private Diet.MealType mealType;
    private String foodName;
    private Double calories;
    private Double carbohydrate;
    private Double protein;
    private Double totalFat;
    private Double sugar;
    private Double dietaryFiber;
    private Double sodium;
    private LocalDateTime mealTime;
    private Diet.DataSource dataSource;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DietResponse from(Diet diet) {
        return DietResponse.builder()
                .id(diet.getId())
                .userId(diet.getUserId())
                .mealType(diet.getMealType())
                .foodName(diet.getFoodName())
                .calories(diet.getCalories())
                .carbohydrate(diet.getCarbohydrate())
                .protein(diet.getProtein())
                .totalFat(diet.getTotalFat())
                .sugar(diet.getSugar())
                .dietaryFiber(diet.getDietaryFiber())
                .sodium(diet.getSodium())
                .mealTime(diet.getMealTime())
                .dataSource(diet.getDataSource())
                .createdAt(diet.getCreatedAt())
                .updatedAt(diet.getUpdatedAt())
                .build();
    }
}