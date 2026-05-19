package com.checkdang.controller;

import com.checkdang.domain.Diet;
import com.checkdang.domain.Exercise;
import com.checkdang.domain.Sleep;
import com.checkdang.domain.User;
import com.checkdang.repository.DietRepository;
import com.checkdang.repository.ExerciseRepository;
import com.checkdang.repository.SleepRepository;
import com.checkdang.repository.UserRepository;
import com.checkdang.service.AiAnalysisClient;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/health")
    public ResponseEntity<AiHealthReportResponse> getHealthReport(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        LocalDateTime reportTo = to == null ? LocalDateTime.now() : to;
        LocalDateTime reportFrom = from == null ? reportTo.minusDays(Math.max(1, Math.min(days, 30))) : from;

        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        List<Diet> diets = dietRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(String.valueOf(user.getId()), reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();
        List<Sleep> sleeps = sleepRepository
                .findWithStagesByUserIdAndRange(String.valueOf(user.getId()), reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();
        List<Exercise> exercises = exerciseRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(String.valueOf(user.getId()), reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();

        Map<String, Object> reportData = buildReportData(user, reportFrom, reportTo, diets, sleeps, exercises);
        String report = aiAnalysisClient.analyzeHealthReport(reportData);

        return ResponseEntity.ok(new AiHealthReportResponse(
                reportFrom,
                reportTo,
                new AiReportSourceCount(diets.size(), sleeps.size(), exercises.size()),
                report
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
