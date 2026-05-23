package com.checkdang.repository;

import com.checkdang.domain.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    Optional<AiAnalysis> findTopByUserIdAndAnalysisTypeAndPeriodFromAndPeriodToOrderByCreatedAtDesc(
            String userId,
            AiAnalysis.AnalysisType analysisType,
            LocalDateTime periodFrom,
            LocalDateTime periodTo);
}
