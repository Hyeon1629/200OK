package com.checkdang.service;

import com.checkdang.domain.AiAnalysis;
import com.checkdang.repository.AiAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAnalysisService {

    private final AiAnalysisRepository aiAnalysisRepository;

    public Optional<String> findCached(String userId, AiAnalysis.AnalysisType type,
                                       LocalDateTime from, LocalDateTime to) {
        return aiAnalysisRepository
                .findTopByUserIdAndAnalysisTypeAndPeriodFromAndPeriodToOrderByCreatedAtDesc(
                        userId, type, from, to)
                .map(AiAnalysis::getResult);
    }

    @Transactional
    public void save(String userId, AiAnalysis.AnalysisType type,
                     LocalDateTime from, LocalDateTime to, String result) {
        aiAnalysisRepository.save(AiAnalysis.builder()
                .userId(userId)
                .analysisType(type)
                .periodFrom(from)
                .periodTo(to)
                .result(result)
                .build());
    }
}
