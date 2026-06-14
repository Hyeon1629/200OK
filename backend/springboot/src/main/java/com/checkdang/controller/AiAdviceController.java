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
     * 파라미터 없이 로그인 사용자의 실제 식단(기본 최근 7일)을 분석하는 정식 경로.
     * 이 메서드는 실제 DB를 읽으므로 이름이 'demo'였던 구 경로(`/demo-diet-advice`)는
     * 의미가 맞지 않아 deprecated 처리하고, 정식 경로 `/diet-advice/recent`를 함께 매핑한다.
     * 프론트는 준비되면 정식 경로로 1줄 교체하면 되고, 그 전까지 구 경로도 동일하게 동작한다.
     * (인증 필수 경로 — JWT 이미 전달됨)
     */
    @GetMapping({"/diet-advice/recent", "/demo-diet-advice"})
    public AiAdviceResponse getRecentDietAdvice(@AuthenticationPrincipal Jwt jwt) {
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
