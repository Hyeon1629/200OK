package com.checkdang.repository;

import com.checkdang.domain.Diet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DietRepository extends JpaRepository<Diet, Long> {

    // User가 DynamoDB 엔티티이므로 userId(String)로 중복 체크
    boolean existsByUserIdAndMealTimeAndFoodName(String userId, LocalDateTime mealTime, String foodName);

    List<Diet> findByUserIdAndMealTimeBetweenOrderByMealTimeDesc(String userId, LocalDateTime from, LocalDateTime to);
}