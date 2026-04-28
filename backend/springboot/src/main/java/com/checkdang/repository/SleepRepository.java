package com.checkdang.repository;

import com.checkdang.domain.Sleep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SleepRepository extends JpaRepository<Sleep, Long> {

    // User가 DynamoDB 엔티티이므로 userId(String)로 중복 체크
    boolean existsByUserIdAndSleepStart(String userId, LocalDateTime sleepStart);

    List<Sleep> findByUserIdAndSleepStartBetweenOrderBySleepStartDesc(String userId, LocalDateTime from, LocalDateTime to);
}
