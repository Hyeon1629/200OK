package com.checkdang.repository;

import com.checkdang.domain.Sleep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SleepRepository extends JpaRepository<Sleep, Long> {

    boolean existsByUserIdAndSourceId(String userId, String sourceId);

    boolean existsByUserIdAndSleepTime(String userId, LocalDateTime sleepTime);

    @Query("SELECT DISTINCT s FROM Sleep s LEFT JOIN FETCH s.stages " +
           "WHERE s.userId = :userId AND s.sleepTime BETWEEN :from AND :to " +
           "ORDER BY s.sleepTime DESC")
    List<Sleep> findWithStagesByUserIdAndRange(
            @Param("userId") String userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
