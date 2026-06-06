package com.checkdang.controller;

import com.checkdang.domain.AiAnalysis;
import com.checkdang.domain.User;
import com.checkdang.dto.AiAdviceResponse;
import com.checkdang.dto.DietResponse;
import com.checkdang.repository.UserRepository;
import com.checkdang.service.AiAnalysisClient;
import com.checkdang.service.AiAnalysisService;
import com.checkdang.service.DietService;
import com.checkdang.service.UserService;
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
    private final UserService userService;

    @GetMapping("/diet-advice")
    public AiAdviceResponse getDietAdvice(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return advise(jwt, from, to);
    }

    /**
     * (구 demo) 파라미터 없이 호출돼도 로그인 사용자의 실제 식단(기본 최근 7일)으로 분석한다.
     * 과거엔 "김밥과 라면" 고정 샘플을 반환했으나, 프론트가 이 경로를 그대로 쓰면서 실데이터를
     * 받도록 실엔드포인트와 동일 로직으로 위임한다. (인증 필수 경로 — JWT 이미 전달됨)
     */
    @GetMapping("/demo-diet-advice")
    public AiAdviceResponse getDemoDietAdvice(@AuthenticationPrincipal Jwt jwt) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(7);
        return advise(jwt, from, to);
    }

    private AiAdviceResponse advise(Jwt jwt, LocalDateTime from, LocalDateTime to) {
        String userEmail = userService.resolveEmail(jwt);
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
}
