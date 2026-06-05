package com.checkdang.service;

import com.checkdang.domain.Diet;
import com.checkdang.domain.Exercise;
import com.checkdang.domain.PainRecord;
import com.checkdang.domain.Sleep;
import com.checkdang.domain.User;
import com.checkdang.dto.PainAnalysisResponse;
import com.checkdang.repository.DietRepository;
import com.checkdang.repository.ExerciseRepository;
import com.checkdang.repository.PainRecordRepository;
import com.checkdang.repository.SleepRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 통증 1건 + 전날~당일 생활데이터(식단·운동·수면) + 최근 1주일 같은 부위 통증을 모아
 * FastAPI {@code /analyze/pain} 로 위임(혈당은 FastAPI가 DynamoDB에서 직접 조회)하고,
 * 결과(aiCause/aiFirstAid)를 통증 row 에 저장한다. (방식 B)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PainAnalysisService {

    private final UserRepository userRepository;
    private final PainRecordRepository painRecordRepository;
    private final DietRepository dietRepository;
    private final ExerciseRepository exerciseRepository;
    private final SleepRepository sleepRepository;
    private final AiAnalysisClient aiAnalysisClient;

    @Transactional
    public PainAnalysisResponse analyze(String email, Long painRecordId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        String userId = String.valueOf(user.getId());

        PainRecord pain = painRecordRepository.findById(painRecordId)
                .orElseThrow(() -> new IllegalArgumentException("통증 기록을 찾을 수 없습니다."));
        if (!pain.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 통증 기록만 분석할 수 있습니다.");
        }

        // 날짜 범위: 식단/운동/수면/혈당 = 전날 00:00 ~ 당일 23:59
        LocalDate painDate = pain.getRecordedAt().toLocalDate();
        LocalDateTime from = painDate.minusDays(1).atStartOfDay();
        LocalDateTime to = painDate.atTime(LocalTime.MAX);
        // 과거 통증 = 최근 1주일(같은 부위)
        LocalDateTime weekFrom = painDate.minusDays(7).atStartOfDay();

        List<Diet> diets = dietRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, from, to);
        List<Exercise> exercises = exerciseRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, from, to);
        List<Sleep> sleeps = sleepRepository
                .findWithStagesByUserIdAndRange(userId, from, to);
        // 최근 1주일 같은 부위 통증 (이번 분석 대상 본인은 제외)
        List<PainRecord> painHistory = painRecordRepository
                .findByUserIdAndBodyPartAndRecordedAtBetweenOrderByRecordedAtDesc(
                        userId, pain.getBodyPart(), weekFrom, to)
                .stream().filter(p -> !p.getId().equals(pain.getId())).toList();

        // payload 조립 (FastAPI 가 user_id + date 로 혈당 자체 조회)
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", userId);
        payload.put("date", painDate.toString());
        payload.put("pain", painMap(pain));
        payload.put("pain_history", painHistory.stream().map(this::painMap).toList());
        payload.put("diets", diets.stream().map(this::dietMap).toList());
        payload.put("exercises", exercises.stream().map(this::exerciseMap).toList());
        payload.put("sleeps", sleeps.stream().map(this::sleepMap).toList());

        // FastAPI 위임 → { ai_cause, ai_first_aid }
        Map<String, String> result = aiAnalysisClient.analyzePain(payload);

        // 결과 저장 (통증 1건에 귀속)
        pain.setAiCause(result.get("ai_cause"));
        pain.setAiFirstAid(result.get("ai_first_aid"));
        painRecordRepository.save(pain);

        return PainAnalysisResponse.builder()
                .painRecordId(pain.getId())
                .aiCause(result.get("ai_cause"))
                .aiFirstAid(result.get("ai_first_aid"))
                .build();
    }

    private Map<String, Object> painMap(PainRecord p) {
        Map<String, Object> m = new HashMap<>();
        m.put("bodyPart", p.getBodyPart().name());
        m.put("intensity", p.getIntensity());
        m.put("qualityTags", p.getQualityTags());
        m.put("situationTags", p.getSituationTags());
        m.put("recordedAt", String.valueOf(p.getRecordedAt()));
        return m;
    }

    private Map<String, Object> dietMap(Diet d) {
        Map<String, Object> m = new HashMap<>();
        m.put("foodName", d.getFoodName());
        m.put("mealType", d.getMealType() != null ? d.getMealType().name() : null);
        m.put("calories", d.getCalories());
        m.put("carbohydrate", d.getCarbohydrate());
        m.put("protein", d.getProtein());
        m.put("sugar", d.getSugar());
        m.put("sodium", d.getSodium());
        m.put("recordedAt", String.valueOf(d.getRecordedAt()));
        return m;
    }

    private Map<String, Object> exerciseMap(Exercise e) {
        Map<String, Object> m = new HashMap<>();
        m.put("exerciseName", e.getExerciseName());
        m.put("duration", e.getDuration());
        m.put("sets", e.getSets());
        m.put("reps", e.getReps());
        m.put("weightKg", e.getWeightKg());
        m.put("recordedAt", String.valueOf(e.getRecordedAt()));
        return m;
    }

    private Map<String, Object> sleepMap(Sleep s) {
        Map<String, Object> m = new HashMap<>();
        m.put("sleepTime", String.valueOf(s.getSleepTime()));
        m.put("wakeTime", String.valueOf(s.getWakeTime()));
        m.put("duration", s.getDuration());
        m.put("quality", s.getQuality());
        return m;
    }
}
