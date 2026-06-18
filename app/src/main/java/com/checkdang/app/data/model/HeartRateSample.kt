package com.checkdang.app.data.model

/**
 * 외부 헬스 소스(Samsung Health)에서 가져온 심박수 단일 측정 샘플.
 * 시계열 데이터 — 하루에 다수의 샘플이 존재할 수 있다.
 */
data class HeartRateSample(
    val timestamp: Long,   // epoch millis
    val bpm: Int
)
