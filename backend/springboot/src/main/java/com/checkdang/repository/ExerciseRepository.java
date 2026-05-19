package com.checkdang.repository;

import com.checkdang.domain.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    boolean existsByUserIdAndSourceId(String userId, String sourceId);

    boolean existsByUserIdAndRecordedAtAndExerciseName(String userId, LocalDateTime recordedAt, String exerciseName);

    List<Exercise> findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(String userId, LocalDateTime from, LocalDateTime to);
}
