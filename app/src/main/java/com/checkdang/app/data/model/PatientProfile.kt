package com.checkdang.app.data.model

data class PatientProfile(
    val nickname: String = "",
    val birthDate: String = "",      // "YYYY-MM-DD"
    val gender: Gender = Gender.NONE,
    val heightCm: Float = 0f,
    val weightKg: Float = 0f,
    val diabetesType: DiabetesType = DiabetesType.NONE,
    val diagnosedAt: String = "",    // "YYYY-MM" (진단 시점, 빈 문자열이면 미입력)
    val fastingTargetMgdl: Int = 0,  // 0이면 미입력 — 기본값은 가이드라인(70~99) 사용
    val postMealTargetMgdl: Int = 0  // 0이면 미입력 — 기본값은 가이드라인(<140) 사용
)

enum class Gender { MALE, FEMALE, NONE }

enum class DiabetesType(val labelKr: String) {
    NONE("선택 안 함"),
    TYPE_1("1형"),
    TYPE_2("2형"),
    GESTATIONAL("임신성"),
    PRE("당뇨 전단계")
}
