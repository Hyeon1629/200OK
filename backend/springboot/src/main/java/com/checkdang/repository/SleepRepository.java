package com.checkdang.repository;

import com.checkdang.domain.Sleep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SleepRepository extends JpaRepository<Sleep, Long> {

    // User가 DynamoDB 엔티티이므로 userId(String)로 중복 체크
    boolean existsByUserIdAndSleepStart(String userId, LocalDateTime sleepStart);

    // stages를 JOIN FETCH로 함께 로딩 — N+1 방지 (Lazy 컬렉션을 별도 쿼리 없이 한 번에 조회)
    @Query("SELECT DISTINCT s FROM Sleep s LEFT JOIN FETCH s.stages " +
           "WHERE s.userId = :userId AND s.sleepStart BETWEEN :from AND :to " +
           "ORDER BY s.sleepStart DESC")
    List<Sleep> findWithStagesByUserIdAndRange(
            @Param("userId") String userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
