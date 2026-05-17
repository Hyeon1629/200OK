package com.checkdang.app.data.samsunghealth

/**
 * Samsung Health Data SDK 권한 카테고리 (READ-only).
 *
 * 실제 SDK [com.samsung.android.sdk.health.data.permission.Permission] 으로의 매핑은
 * [SamsungHealthRepository.toSdkDataType] 에서 수행한다.
 */
enum class HealthDataPermission(val labelKr: String) {
    STEPS("걸음 수"),
    EXERCISE("운동"),
    NUTRITION("식사"),
    SLEEP("수면"),
    WEIGHT("체중"),
    BLOOD_GLUCOSE("혈당");

    companion object {
        val ALL: List<HealthDataPermission> = values().toList()
    }
}
