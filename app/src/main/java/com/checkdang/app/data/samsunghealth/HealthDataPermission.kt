package com.checkdang.app.data.samsunghealth

/**
 * Samsung Health Data SDK 권한 카테고리 (5종, READ-only).
 *
 * `sdkConstant` 는 Partner Apps Program 승인 + AAR 수령 후 SDK 의 실제 상수로 교체.
 * 현재는 placeholder 로 표기되어 있으며, SDK 호출 경로가 활성화되기 전까지는 식별자로만 사용한다.
 *
 * @see docs/STEP11_samsung_health.md §3 권한 모델
 */
enum class HealthDataPermission(
    val sdkConstant: String,
    val labelKr: String
) {
    STEPS(sdkConstant = "TBD_STEPS_READ", labelKr = "걸음 수"),
    EXERCISE(sdkConstant = "TBD_EXERCISE_READ", labelKr = "운동"),
    NUTRITION(sdkConstant = "TBD_NUTRITION_READ", labelKr = "식사"),
    SLEEP(sdkConstant = "TBD_SLEEP_READ", labelKr = "수면"),
    WEIGHT(sdkConstant = "TBD_WEIGHT_READ", labelKr = "체중");

    companion object {
        val ALL: List<HealthDataPermission> = values().toList()
    }
}
