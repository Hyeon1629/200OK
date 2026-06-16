package com.checkdang.controller;

import com.checkdang.domain.User;
import com.checkdang.dto.GlucosePredictionResponse;
import com.checkdang.repository.DietRepository;
import com.checkdang.repository.InsulinRecordRepository;
import com.checkdang.repository.UserRepository;
import com.checkdang.service.GlucosePredictionClient;
import com.checkdang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 혈당 3시간 예측 — predictor 프록시 (아키텍처 A, on-demand, 저장 없음).
 * 인증된 사용자의 carbs(diet)·bolus(insulin RAPID)를 RDS 에서 모아 predictor 에 전달한다.
 * glucose 는 predictor 가 DynamoDB 에서 자체 조회한다.
 */
@RestController
@RequestMapping("/api/ai/predict")
@RequiredArgsConstructor
public class PredictionController {

    private static final Set<String> BOLUS_TYPES = Set.of("RAPID", "속효성");

    private final UserService userService;
    private final UserRepository userRepository;
    private final DietRepository dietRepository;
    private final InsulinRecordRepository insulinRecordRepository;
    private final GlucosePredictionClient predictionClient;

    @PostMapping("/blood-glucose")
    public GlucosePredictionResponse predictBloodGlucose(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate target = date != null ? date : LocalDate.now();
        String userEmail = userService.resolveEmail(jwt);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        String userId = String.valueOf(user.getId());

        // predictor 가 glucose 를 prev_date+date 로 읽으므로 동일 2일 범위로 넉넉히 수집
        // (predictor build_features 가 실제 288 그리드 밖 이벤트는 자동 클립)
        LocalDateTime from = target.minusDays(1).atStartOfDay();
        LocalDateTime to = target.atTime(23, 59, 59);

        List<Map<String, Object>> carbs = dietRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, from, to)
                .stream()
                .filter(d -> d.getCarbohydrate() != null && d.getCarbohydrate() > 0)
                .map(d -> event(d.getRecordedAt(), d.getCarbohydrate()))
                .toList();

        List<Map<String, Object>> bolus = insulinRecordRepository
                .findByUserIdAndInjectedAtBetweenOrderByInjectedAtAsc(userId, from, to)
                .stream()
                .filter(r -> r.getInsulinType() != null
                        && BOLUS_TYPES.contains(r.getInsulinType().trim().toUpperCase()))
                .map(r -> event(r.getInjectedAt(), r.getDosage().doubleValue()))
                .toList();

        return predictionClient.predict(userId, target.toString(), carbs, bolus);
    }

    private Map<String, Object> event(LocalDateTime ts, double value) {
        return Map.of("timestamp", ts.toString(), "value", value);
    }
}
