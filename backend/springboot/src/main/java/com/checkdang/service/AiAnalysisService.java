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
                        userId, type, normalize(from), normalize(to))
                .map(AiAnalysis::getResult);
    }

    @Transactional
    public void save(String userId, AiAnalysis.AnalysisType type,
                     LocalDateTime from, LocalDateTime to, String result) {
        aiAnalysisRepository.save(AiAnalysis.builder()
                .userId(userId)
                .analysisType(type)
                .periodFrom(normalize(from))
                .periodTo(normalize(to))
                .result(result)
                .build());
    }

    /**
     * 캐시 키 정규화 — 기간을 '일(day)' 단위로 절삭한다.
     * 리포트는 to=now() 라 매 호출 timestamp 가 달라 캐시 적중이 0% 였다.
     * 같은 날·같은 기간(days) 호출이 동일 키로 묶여 하루 1회만 Gemini 를 호출하고
     * 이후엔 저장분을 반환한다(비용 절감). 데이터 조회 window 는 컨트롤러에서 원본 사용.
     */
    private static LocalDateTime normalize(LocalDateTime t) {
        return t.toLocalDate().atStartOfDay();
    }
}
