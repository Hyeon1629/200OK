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
     * 사용자의 AI 본문 캐시(리포트·식단조언 전부)를 무효화한다.
     * 식단/수면/운동이 동기화·삭제되면 같은 기간 키여도 내용물이 달라지므로,
     * 이후 호출이 저장된 옛 본문이 아니라 새 데이터로 다시 생성되도록 캐시를 비운다.
     * (식단 변경이 종합 리포트에도 영향을 주므로 종류를 가리지 않고 통째로 제거)
     */
    @Transactional
    public void evictUser(String userId) {
        aiAnalysisRepository.deleteByUserId(userId);
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
