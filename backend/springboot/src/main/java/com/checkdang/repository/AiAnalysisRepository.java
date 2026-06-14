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

    // 데이터(식단/수면/운동) 변경 시 해당 사용자의 AI 본문 캐시를 통째로 무효화하기 위한 삭제.
    void deleteByUserId(String userId);
}
