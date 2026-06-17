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

data class PainRecord(
    val id: String = UUID.randomUUID().toString(),
    val bodyPart: BodyPart,
    val intensity: Int,                              // 1–5
    val qualityTags: List<String> = emptyList(),     // 통증 성질 (PainTaxonomy.QUALITY)
    val situationTags: List<String> = emptyList(),   // 통증 상황 (PainTaxonomy.SITUATION)
    val recordedAt: Long = System.currentTimeMillis(),
) {
    /** 기록 목록/요약에 표시할 태그 한 줄 (성질 + 상황) */
    val tagSummary: String
        get() = (qualityTags + situationTags).joinToString(" · ").ifEmpty { "기록된 태그 없음" }
}
