package com.checkdang.controller;

import com.checkdang.domain.AiAnalysis;
import com.checkdang.domain.Diet;
import com.checkdang.domain.Exercise;
import com.checkdang.domain.Sleep;
import com.checkdang.domain.User;
import com.checkdang.repository.DietRepository;
import com.checkdang.repository.ExerciseRepository;
import com.checkdang.repository.SleepRepository;
import com.checkdang.repository.UserRepository;
import com.checkdang.service.AiAnalysisClient;
import com.checkdang.service.AiAnalysisService;
import com.checkdang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai/reports")
@RequiredArgsConstructor
public class GeminiReportController {

    private static final int MAX_ROWS_PER_SECTION = 30;

    private final UserRepository userRepository;
    private final DietRepository dietRepository;
    private final SleepRepository sleepRepository;
    private final ExerciseRepository exerciseRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final AiAnalysisService aiAnalysisService;
    private final UserService userService;

    @GetMapping("/health")
    public ResponseEntity<AiHealthReportResponse> getHealthReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        LocalDateTime reportTo = to == null ? LocalDateTime.now() : to;
        LocalDateTime reportFrom = from == null ? reportTo.minusDays(Math.max(1, Math.min(days, 30))) : from;

        User user = userRepository.findByEmail(userService.resolveEmail(jwt))
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        String userId = String.valueOf(user.getId());

        // 1) 소스 카운트 먼저 (싼 DB 조회 — Gemini 아님)
        List<Diet> diets = dietRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();
        List<Sleep> sleeps = sleepRepository
                .findWithStagesByUserIdAndRange(userId, reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();
        List<Exercise> exercises = exerciseRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();

        AiReportSourceCount sourceCount =
                new AiReportSourceCount(diets.size(), sleeps.size(), exercises.size());

        // 2) 3종 모두 0건이면 캐시·Gemini 없이 안내 (옛 리포트가 0/0/0으로 새어 나가는 것 방지)
        if (diets.isEmpty() && sleeps.isEmpty() && exercises.isEmpty()) {
            return ResponseEntity.ok(new AiHealthReportResponse(
                    reportFrom, reportTo, sourceCount,
                    "## 종합 리포트\n\n아직 기록된 데이터가 없어요. 식단·운동·수면을 먼저 기록하면 AI가 분석해 드릴게요."
            ));
        }

        // 3) 데이터가 있을 때만 캐시 조회 — HIT 시에도 실제 sourceCount 반환
        Optional<String> cached = aiAnalysisService.findCached(
                userId, AiAnalysis.AnalysisType.HEALTH_REPORT, reportFrom, reportTo);
        if (cached.isPresent()) {
            return ResponseEntity.ok(new AiHealthReportResponse(
                    reportFrom, reportTo, sourceCount, cached.get()
            ));
        }

        // 4) MISS → Gemini 생성 + 저장
        Map<String, Object> reportData = buildReportData(user, reportFrom, reportTo, diets, sleeps, exercises);
        String report = aiAnalysisClient.analyzeHealthReport(reportData);
        aiAnalysisService.save(userId, AiAnalysis.AnalysisType.HEALTH_REPORT, reportFrom, reportTo, report);

        return ResponseEntity.ok(new AiHealthReportResponse(
                reportFrom, reportTo, sourceCount, report
        ));
    }

    private Map<String, Object> buildReportData(
            User user,
            LocalDateTime from,
            LocalDateTime to,
            List<Diet> diets,
            List<Sleep> sleeps,
            List<Exercise> exercises) {

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("user", userToMap(user));
        request.put("from", from.toString());
        request.put("to", to.toString());
        request.put("diets", diets.stream().map(this::dietToMap).toList());
        request.put("sleeps", sleeps.stream().map(this::sleepToMap).toList());
        request.put("exercises", exercises.stream().map(this::exerciseToMap).toList());
        return request;
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "name", user.getName());
        putIfPresent(map, "email", user.getEmail());
        return map;
    }

    private Map<String, Object> dietToMap(Diet diet) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "recordedAt", diet.getRecordedAt());
        putIfPresent(map, "mealType", diet.getMealType());
        putIfPresent(map, "foodName", diet.getFoodName());
        putIfPresent(map, "calories", diet.getCalories());
        putIfPresent(map, "carbohydrate", diet.getCarbohydrate());
        putIfPresent(map, "protein", diet.getProtein());
        putIfPresent(map, "totalFat", diet.getTotalFat());
        putIfPresent(map, "sugar", diet.getSugar());
        putIfPresent(map, "dietaryFiber", diet.getDietaryFiber());
        putIfPresent(map, "sodium", diet.getSodium());
        return map;
    }

    private Map<String, Object> sleepToMap(Sleep sleep) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "sleepTime", sleep.getSleepTime());
        putIfPresent(map, "wakeTime", sleep.getWakeTime());
        putIfPresent(map, "durationMinutes", sleep.getDuration());
        putIfPresent(map, "quality", sleep.getQuality());
        return map;
    }

    private Map<String, Object> exerciseToMap(Exercise exercise) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "recordedAt", exercise.getRecordedAt());
        putIfPresent(map, "exerciseName", exercise.getExerciseName());
        putIfPresent(map, "durationMinutes", exercise.getDuration());
        putIfPresent(map, "sets", exercise.getSets());
        putIfPresent(map, "reps", exercise.getReps());
        putIfPresent(map, "weightKg", exercise.getWeightKg());
        return map;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value.toString());
        }
    }

    public record AiHealthReportResponse(
            LocalDateTime from,
            LocalDateTime to,
            AiReportSourceCount sourceCount,
            String report
    ) {
    }

    public record AiReportSourceCount(
            int diets,
            int sleeps,
            int exercises
    ) {
    }
}
