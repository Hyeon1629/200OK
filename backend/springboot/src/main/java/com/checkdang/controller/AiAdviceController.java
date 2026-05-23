package com.checkdang.controller;

import com.checkdang.domain.AiAnalysis;
import com.checkdang.domain.User;
import com.checkdang.dto.AiAdviceResponse;
import com.checkdang.dto.DietResponse;
import com.checkdang.repository.UserRepository;
import com.checkdang.service.AiAnalysisClient;
import com.checkdang.service.AiAnalysisService;
import com.checkdang.service.DietService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAdviceController {

    private final DietService dietService;
    private final AiAnalysisClient aiAnalysisClient;
    private final UserRepository userRepository;
    private final AiAnalysisService aiAnalysisService;

    @GetMapping("/diet-advice")
    public AiAdviceResponse getDietAdvice(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        String userEmail = jwt.getClaimAsString("email");
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        String userId = String.valueOf(user.getId());

        Optional<String> cached = aiAnalysisService.findCached(userId, AiAnalysis.AnalysisType.DIET_ADVICE, from, to);
        if (cached.isPresent()) {
            return new AiAdviceResponse(cached.get());
        }

        List<DietResponse> diets = dietService.getDiets(userEmail, from, to);
        String answer = aiAnalysisClient.analyzeDiet(diets);
        aiAnalysisService.save(userId, AiAnalysis.AnalysisType.DIET_ADVICE, from, to, answer);
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
