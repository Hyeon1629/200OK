package com.checkdang.app.data.samsunghealth

import java.time.Instant
import java.time.ZoneId

/**
 * Samsung Health Data SDK 응답을 앱 도메인 모델로 변환.
 *
 * Phase 1 (현재): 단위 변환 헬퍼만 제공. 실제 매핑 함수(toExerciseSummary 등)는
 * SDK 응답 타입이 import 가능해지는 Phase 2 에서 추가한다.
 *
 * @see docs/STEP11_samsung_health.md §7 데이터 모델 매핑
 */
object SamsungHealthMapper {

    // ── 단위 변환 헬퍼 ────────────────────────────────────────────────────────

    /** calorie (cal) → kilocalorie (kcal). SDK 가 cal 또는 J 로 줄 경우 사용. */
    fun kcalFromCalories(calories: Double): Int = (calories / 1000.0).toInt()

    /** joule (J) → kilocalorie (kcal). 1 kcal ≈ 4184 J. */
    fun kcalFromJoules(joules: Double): Int = (joules / 4184.0).toInt()

    /** 초 → 분. */
    fun minutesFromSeconds(seconds: Long): Int = (seconds / 60L).toInt()

    /** 초 → 시간 (Float). 수면 시간용. */
    fun hoursFromSeconds(seconds: Long): Float = seconds / 3600f

    /** 그램(g) → 킬로그램(kg). 체중용. */
    fun kgFromGrams(grams: Double): Float = (grams / 1000.0).toFloat()

    /** epoch millis → "오전 7:30" 형식. UI 표시용. */
    fun formatKoreanTime(epochMillis: Long): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        val mer = if (zdt.hour < 12) "오전" else "오후"
        val disp = if (zdt.hour == 0 || zdt.hour == 12) 12 else zdt.hour % 12
        return "$mer $disp:%02d".format(zdt.minute)
    }

    // ── SDK Response → 도메인 모델 (Phase 2 활성화) ───────────────────────────

    // TODO(samsung-sdk): SDK 응답 타입이 import 가능해진 뒤 아래 함수 시그니처 활성화.
    //
    // fun toExerciseSummary(records: List<SamsungExerciseRecord>): ExerciseSummary { ... }
    // fun toMealSummary(records: List<SamsungNutritionRecord>): MealSummary { ... }
    // fun toSleepSummary(record: SamsungSleepRecord): SleepSummary { ... }
    // fun toStepCount(records: List<SamsungStepRecord>): Int { ... }
    // fun toLatestWeight(records: List<SamsungWeightRecord>): Float? { ... }
}
