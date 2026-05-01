package com.checkdang.app.data.model

import java.util.UUID

enum class BodyView { FRONT, BACK }

enum class BodyPart(val label: String, val view: BodyView) {
    // Front
    HEAD("머리", BodyView.FRONT),
    NECK_FRONT("목 (앞)", BodyView.FRONT),
    LEFT_SHOULDER_FRONT("왼쪽 어깨", BodyView.FRONT),
    RIGHT_SHOULDER_FRONT("오른쪽 어깨", BodyView.FRONT),
    CHEST("가슴", BodyView.FRONT),
    LEFT_ARM_FRONT("왼팔", BodyView.FRONT),
    RIGHT_ARM_FRONT("오른팔", BodyView.FRONT),
    ABDOMEN("복부", BodyView.FRONT),
    LEFT_HIP_FRONT("왼쪽 골반", BodyView.FRONT),
    RIGHT_HIP_FRONT("오른쪽 골반", BodyView.FRONT),
    LEFT_THIGH_FRONT("왼쪽 허벅지", BodyView.FRONT),
    RIGHT_THIGH_FRONT("오른쪽 허벅지", BodyView.FRONT),
    LEFT_KNEE("왼쪽 무릎", BodyView.FRONT),
    RIGHT_KNEE("오른쪽 무릎", BodyView.FRONT),
    LEFT_SHIN("왼쪽 정강이", BodyView.FRONT),
    RIGHT_SHIN("오른쪽 정강이", BodyView.FRONT),
    // Back
    NECK_BACK("목 (뒤)", BodyView.BACK),
    UPPER_BACK("등 위", BodyView.BACK),
    LOWER_BACK("허리", BodyView.BACK),
    LEFT_SHOULDER_BACK("왼쪽 어깨 (뒤)", BodyView.BACK),
    RIGHT_SHOULDER_BACK("오른쪽 어깨 (뒤)", BodyView.BACK),
}

enum class PainType(val label: String) {
    SHARP("찌르는 통증"),
    DULL("둔한 통증"),
    BURNING("타는 느낌"),
    THROBBING("욱신거림"),
    STIFFNESS("뻣뻣함"),
    NUMBNESS("저림"),
}

enum class CorrelationLevel(val label: String) {
    HIGH("높음"),
    MEDIUM("중간"),
    LOW("낮음"),
}

data class Correlation(
    val factor: String,
    val level: CorrelationLevel,
    val description: String,
)

data class PainRecord(
    val id: String = UUID.randomUUID().toString(),
    val bodyPart: BodyPart,
    val intensity: Int,          // 1–5
    val painTypes: List<PainType>,
    val recordedAt: Long = System.currentTimeMillis(),
)

data class AIAnalysisResult(
    val painRecord: PainRecord,
    val summary: String,
    val correlations: List<Correlation>,
    val recommendation: String,
)
