package com.checkdang.repository;

import com.checkdang.domain.Diet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DietRepository extends JpaRepository<Diet, Long> {

    boolean existsByUserIdAndSourceId(String userId, String sourceId);

    boolean existsByUserIdAndRecordedAtAndFoodName(String userId, LocalDateTime recordedAt, String foodName);

    List<Diet> findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(String userId, LocalDateTime from, LocalDateTime to);
}
