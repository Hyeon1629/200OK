package com.checkdang.service;

import com.checkdang.dto.GlucosePredictionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 혈당 예측 predictor 호출 클라이언트 (아키텍처 A).
 * glucose 는 predictor 가 DynamoDB 에서 자체 조회하고, carbs/bolus 는 Spring 이
 * 모아 body 로 전달한다. predictor 의 422(데이터 부족)는 그대로 전파한다.
 */
@Service
@RequiredArgsConstructor
public class GlucosePredictionClient {

    private final RestClient restClient = RestClient.create();

    @Value("${predictor.server-url}")
    private String predictorServerUrl;

    public GlucosePredictionResponse predict(
            String userId,
            String date,
            List<Map<String, Object>> carbs,
            List<Map<String, Object>> bolus) {

        Map<String, Object> requestBody = Map.of("carbs", carbs, "bolus", bolus);

        Map<?, ?> response = restClient.post()
                .uri(predictorServerUrl + "/predict/blood-glucose/{userId}?date={date}", userId, date)
                .body(requestBody)
                .retrieve()
                .onStatus(s -> s.value() == 422, (req, res) -> {
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "예측에 필요한 혈당 데이터가 부족합니다.");
                })
                .body(Map.class);

        if (response == null || response.get("predictions") == null) {
            throw new IllegalStateException("Predictor 응답이 비어 있습니다.");
        }

        @SuppressWarnings("unchecked")
        List<Number> raw = (List<Number>) response.get("predictions");
        List<Double> predictions = raw.stream().map(Number::doubleValue).toList();
        Object h = response.get("horizon_minutes");
        int horizon = (h instanceof Number) ? ((Number) h).intValue() : 180;

        return new GlucosePredictionResponse(predictions, horizon);
    }
}
