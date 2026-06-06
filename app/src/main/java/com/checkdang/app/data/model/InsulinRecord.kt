package com.checkdang.app.data.model

import java.util.UUID

/** 인슐린 종류 (최소 분류). */
enum class InsulinType(val label: String) {
    RAPID("속효성"),
    LONG("지속형(기저)"),
    MIXED("혼합형"),
    OTHER("기타"),
}

/**
 * 사용자가 직접 입력하는 인슐린 주입 기록 (수동 입력 전용 · 로컬 저장).
 *
 * 혈당과 동일한 "기록" 타임라인에 시간순으로 함께 표시된다. 혈당처럼 백엔드로
 * push 하지는 않으며(백엔드 인슐린 도메인 미정), MockDataProvider 가 SharedPreferences 로 영속화한다.
 */
data class InsulinRecord(
    val id: String = UUID.randomUUID().toString(),
    val units: Float,            // 주입 단위 (U)
    val type: InsulinType,
    val injectedAt: Long,        // epoch millis
    val memo: String? = null,
) {
    /** "6" 또는 "5.5" 형태(불필요한 .0 제거). */
    val unitsLabel: String
        get() = if (units % 1f == 0f) units.toInt().toString() else units.toString()
}
