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

        Map<?, ?> response = restClient.post()
                .uri(aiServerUrl + "/analyze/diet")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("answer") == null) {
            throw new IllegalStateException("AI server response is empty.");
        }

        return (String) response.get("answer");
    }
}
