package com.checkdang.controller;

import com.checkdang.dto.AiAdviceResponse;
import com.checkdang.dto.DietResponse;
import com.checkdang.service.AiAnalysisClient;
import com.checkdang.service.DietService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAdviceController {

    private final DietService dietService;
    private final AiAnalysisClient aiAnalysisClient;

    @GetMapping("/diet-advice")
    public AiAdviceResponse getDietAdvice(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        String userEmail = principal.getUsername();
        List<DietResponse> diets = dietService.getDiets(userEmail, from, to);

        String answer = aiAnalysisClient.analyzeDiet(diets);
        return new AiAdviceResponse(answer);
    }

    @GetMapping("/demo-diet-advice")
    public AiAdviceResponse getDemoDietAdvice() {
        List<DietResponse> diets = List.of(
                DietResponse.builder()
                        .userId("android-demo-user")
                        .sourceId("android-demo-lunch")
                        .mealType(com.checkdang.domain.Diet.MealType.LUNCH)
                        .foodName("김밥과 라면")
                        .calories(950.0)
                        .carbohydrate(125.0)
                        .protein(22.0)
                        .totalFat(34.0)
                        .sugar(12.0)
                        .dietaryFiber(6.0)
                        .sodium(1850.0)
                        .recordedAt(LocalDateTime.now())
                        .dataSource(com.checkdang.domain.Diet.DataSource.MANUAL)
                        .build()
        );

        String answer = aiAnalysisClient.analyzeDiet(diets);
        return new AiAdviceResponse(answer);
    }
}
