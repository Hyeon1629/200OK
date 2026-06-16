package com.checkdang.dto;

import java.util.List;

/**
 * 혈당 예측 응답 — predictor 의 미래 {@code horizonMinutes}분(5분 간격) 예측값.
 */
public record GlucosePredictionResponse(
        List<Double> predictions,
        int horizonMinutes
) {
}
