package com.checkdang.service;

import com.checkdang.dto.DietResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAnalysisClient {

    private final RestClient restClient = RestClient.create();

    @Value("${ai.server-url}")
    private String aiServerUrl;

    public String analyzeDiet(List<DietResponse> diets) {
        Map<String, Object> requestBody = Map.of("diets", diets);
        return requestAnswer("/analyze/diet", requestBody);
    }

    public String analyzeHealthReport(Map<String, Object> reportData) {
        return requestAnswer("/analyze/health-report", reportData);
    }

    /**
     * 통증 AI 분석 위임 — FastAPI {@code /analyze/pain}.
     * 식단/리포트와 응답 형식이 달라({@code answer} 단일키가 아니라 {@code ai_cause}/{@code ai_first_aid})
     * 별도 메서드로 둔다.
     */
    public Map<String, String> analyzePain(Map<String, Object> painData) {
        Map<?, ?> response = restClient.post()
                .uri(aiServerUrl + "/analyze/pain")
                .body(painData)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("ai_cause") == null) {
            throw new IllegalStateException("AI server response is empty.");
        }

        return Map.of(
                "ai_cause", String.valueOf(response.get("ai_cause")),
                "ai_first_aid", String.valueOf(response.get("ai_first_aid")));
    }

    private String requestAnswer(String path, Map<String, Object> requestBody) {
        Map<?, ?> response = restClient.post()
                .uri(aiServerUrl + path)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("answer") == null) {
            throw new IllegalStateException("AI server response is empty.");
        }

        return (String) response.get("answer");
    }
}
