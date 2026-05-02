package com.checkdang.service;

import com.checkdang.domain.Exercise;
import com.checkdang.domain.User;
import com.checkdang.dto.ExerciseResponse;
import com.checkdang.dto.ExerciseSyncRequest;
import com.checkdang.dto.SyncResponse;
import com.checkdang.repository.ExerciseRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;

    @Transactional
    public SyncResponse syncFromSamsungHealth(String userEmail, List<ExerciseSyncRequest> requests) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        int saved = 0;
        for (ExerciseSyncRequest req : requests) {
            if (isDuplicate(user.getId(), req)) continue;

            exerciseRepository.save(Exercise.builder()
                    .userId(user.getId())
                    .sourceId(req.getSourceId())
                    .exerciseName(req.getExerciseName())
                    .duration(req.getDuration())
                    .recordedAt(req.getRecordedAt())
                    .sets(req.getSets())
                    .reps(req.getReps())
                    .weightKg(req.getWeightKg())
                    .dataSource(Exercise.DataSource.SAMSUNG_HEALTH)
                    .build());
            saved++;
        }

        return SyncResponse.of(saved, requests.size());
    }

    public List<ExerciseResponse> getExercises(String userEmail, LocalDateTime from, LocalDateTime to) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        return exerciseRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(user.getId(), from, to)
                .stream()
                .map(ExerciseResponse::from)
                .toList();
    }

    private boolean isDuplicate(String userId, ExerciseSyncRequest req) {
        if (req.getSourceId() != null) {
            return exerciseRepository.existsByUserIdAndSourceId(userId, req.getSourceId());
        }
        return exerciseRepository.existsByUserIdAndRecordedAtAndExerciseName(
                userId, req.getRecordedAt(), req.getExerciseName());
    }
}
