package com.checkdang.app.data.samsunghealth

import com.checkdang.app.data.model.ExerciseSession
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.HeartRateSample
import com.checkdang.app.data.model.MealItem
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import com.checkdang.app.util.MealTiming
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.BloodGlucose as SdkBloodGlucose
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession as SdkExerciseSession
import com.samsung.android.sdk.health.data.data.entries.HeartRate as SdkHeartRate
import com.samsung.android.sdk.health.data.data.entries.SleepSession as SdkSleepSession
import com.samsung.android.sdk.health.data.request.DataType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Samsung Health Data SDK 응답을 앱 도메인 모델로 변환.
 *
 * Field 추출은 `HealthDataPoint.getValue(Field<T>)` 를 사용한다. SDK 가 platform-type 으로
 * null 을 반환할 수 있으므로 모든 값을 nullable 로 가정하고 안전 기본값을 채운다.
 */
object SamsungHealthMapper {

    // ── 단위 변환 헬퍼 ────────────────────────────────────────────────────────

    /** calorie (cal) → kilocalorie (kcal). SDK 가 cal 또는 J 로 줄 경우 사용. */
    fun kcalFromCalories(calories: Double): Int = (calories / 1000.0).toInt()

    /** joule (J) → kilocalorie (kcal). 1 kcal ≈ 4184 J. */
    fun kcalFromJoules(joules: Double): Int = (joules / 4184.0).toInt()

    /**
     * 혈당 mmol/L → mg/dL.
     *
     * Samsung Health Data SDK 의 BloodGlucose 값은 **mmol/L** 단위로 내려온다(2026-06-13 실기 logcat 확인:
     * raw 5.55/6.27/6.66 → 100/113/120 mg/dL). 앱 전역 [GlucoseRecord] 는 mg/dL 기준이므로 변환 필수.
     * 1 mmol/L ≈ 18.0182 mg/dL.
     */
    private const val MMOL_TO_MGDL = 18.0182
    fun mgdlFromMmol(mmol: Float): Int = (mmol * MMOL_TO_MGDL).roundToInt()

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

    // ── SDK Response → 도메인 모델 ───────────────────────────────────────────

    /**
     * 운동: DataTypes.EXERCISE read 결과 → ExerciseSummary.
     * 각 HealthDataPoint 의 SESSIONS Field 에서 [SdkExerciseSession] 리스트를 펼쳐 매핑.
     */
    fun toExerciseSummary(dataPoints: List<HealthDataPoint>): ExerciseSummary? {
        if (dataPoints.isEmpty()) return null
        val sessions: List<ExerciseSession> = dataPoints.flatMap { dp ->
            val list: List<SdkExerciseSession> =
                dp.getValue(DataType.ExerciseType.SESSIONS) ?: emptyList()
            val parentType = dp.getValue(DataType.ExerciseType.EXERCISE_TYPE)
            val parentCustomTitle = dp.getValue(DataType.ExerciseType.CUSTOM_TITLE)
            list.map { it.toAppModel(parentType, parentCustomTitle) }
        }
        if (sessions.isEmpty()) return null
        return ExerciseSummary(
            totalMinutes  = sessions.sumOf { it.durationMin },
            goalMinutes   = 60,
            totalCalories = sessions.sumOf { it.calories },
            sessions      = sessions
        )
    }

    private fun SdkExerciseSession.toAppModel(
        parentType: DataType.ExerciseType.PredefinedExerciseType?,
        parentCustomTitle: String?
    ): ExerciseSession {
        val resolvedType = parentType ?: this.exerciseType
        val typeLabel = customTitle?.takeIf { it.isNotBlank() }
            ?: parentCustomTitle?.takeIf { it.isNotBlank() }
            ?: koreanExerciseTypeName(resolvedType)
        return ExerciseSession(
            type        = typeLabel,
            durationMin = duration.toMinutes().toInt(),
            calories    = calories.toInt(),
            startedAt   = formatKoreanTime(startTime.toEpochMilli())
        )
    }

    /**
     * 식사: DataTypes.NUTRITION read 결과 → MealSummary.
     * HealthDataPoint 1개 = 식사 기록 1건.
     */
    fun toMealSummary(dataPoints: List<HealthDataPoint>): MealSummary? {
        if (dataPoints.isEmpty()) return null
        var totalKcal    = 0
        var totalCarbs   = 0f
        var totalProtein = 0f
        var totalFat     = 0f
        val meals: List<MealItem> = dataPoints.map { dp ->
            val kcal     = dp.getValue(DataType.NutritionType.CALORIES) ?: 0f
            val carbs    = dp.getValue(DataType.NutritionType.CARBOHYDRATE) ?: 0f
            val protein  = dp.getValue(DataType.NutritionType.PROTEIN) ?: 0f
            val fat      = dp.getValue(DataType.NutritionType.TOTAL_FAT) ?: 0f
            val mealType = dp.getValue(DataType.NutritionType.MEAL_TYPE)
            val title    = dp.getValue(DataType.NutritionType.TITLE)
            totalKcal    += kcal.toInt()
            totalCarbs   += carbs
            totalProtein += protein
            totalFat     += fat
            MealItem(
                type   = koreanMealTypeName(mealType),
                name   = title?.takeIf { it.isNotBlank() } ?: "기록된 식사",
                kcal   = kcal.toInt(),
                time   = formatKoreanTime(dp.startTime.toEpochMilli()),
                carbsG = carbs.toInt()
            )
        }
        if (meals.isEmpty() && totalKcal == 0) return null
        return MealSummary(
            totalKcal = totalKcal,
            goalKcal  = 2000,
            carbsG    = totalCarbs.toInt(),
            proteinG  = totalProtein.toInt(),
            fatG      = totalFat.toInt(),
            meals     = meals
        )
    }

    /**
     * 수면: DataTypes.SLEEP read 결과 → SleepSummary.
     * 가장 늦은 HealthDataPoint 의 가장 늦은 SleepSession 을 메인 수면으로 채택.
     * SleepStage 가 비어있으면 일반적인 비율로 deep/light/rem 분배.
     */
    fun toSleepSummary(dataPoints: List<HealthDataPoint>): SleepSummary? {
        if (dataPoints.isEmpty()) return null
        val latest = dataPoints.maxByOrNull { it.startTime } ?: return null

        val sessions: List<SdkSleepSession> =
            latest.getValue(DataType.SleepType.SESSIONS) ?: emptyList()
        val main = sessions.maxByOrNull { it.startTime } ?: return null

        val totalHours = main.duration.seconds / 3600f

        var deepHours  = 0f
        var lightHours = 0f
        var remHours   = 0f
        main.stages.orEmpty().forEach { stage ->
            val h = (stage.endTime.epochSecond - stage.startTime.epochSecond) / 3600f
            when (stage.stage) {
                DataType.SleepType.StageType.DEEP  -> deepHours  += h
                DataType.SleepType.StageType.LIGHT -> lightHours += h
                DataType.SleepType.StageType.REM   -> remHours   += h
                else                                -> { /* AWAKE / UNDEFINED 제외 */ }
            }
        }
        if (deepHours == 0f && lightHours == 0f && remHours == 0f) {
            deepHours  = totalHours * 0.20f
            lightHours = totalHours * 0.55f
            remHours   = totalHours * 0.25f
        }

        val sleepScore = latest.getValue(DataType.SleepType.SLEEP_SCORE) ?: 85
        return SleepSummary(
            totalHours = totalHours,
            efficiency = sleepScore,
            deepHours  = deepHours,
            lightHours = lightHours,
            remHours   = remHours,
            bedtime    = formatKoreanTime(main.startTime.toEpochMilli()),
            wakeTime   = formatKoreanTime(main.endTime.toEpochMilli())
        )
    }

    /**
     * 체중: DataTypes.BODY_COMPOSITION read 결과 → 최신 weight (kg).
     */
    fun toLatestWeight(dataPoints: List<HealthDataPoint>): Float? {
        if (dataPoints.isEmpty()) return null
        val latest = dataPoints.maxByOrNull { it.startTime } ?: return null
        return latest.getValue(DataType.BodyCompositionType.WEIGHT)
    }

    /**
     * 혈당: DataTypes.BLOOD_GLUCOSE read 결과 → List&lt;GlucoseRecord&gt;.
     *
     * 한 HealthDataPoint 는 단일 측정(GLUCOSE_LEVEL Field) 또는 시리즈(SERIES_DATA Field) 중
     * 한 가지 형태로 데이터를 담는다. SERIES_DATA 가 있으면 각 항목을 별도 레코드로 펼치고,
     * 없으면 단일 GLUCOSE_LEVEL 을 1개 레코드로 사용.
     *
     * UID 가 없는 데이터포인트는 timestamp 기반 합성 ID 사용.
     */
    fun toGlucoseRecords(dataPoints: List<HealthDataPoint>): List<GlucoseRecord> {
        if (dataPoints.isEmpty()) return emptyList()
        return dataPoints.flatMap { dp ->
            val timing = koreanMealTiming(dp.getValue(DataType.BloodGlucoseType.MEAL_STATUS))
            val series: List<SdkBloodGlucose> =
                dp.getValue(DataType.BloodGlucoseType.SERIES_DATA) ?: emptyList()

            if (series.isNotEmpty()) {
                series.map { sample ->
                    GlucoseRecord(
                        id         = "${dp.uid}-${sample.timestamp.toEpochMilli()}",
                        value      = mgdlFromMmol(sample.glucose),
                        timing     = timing,
                        measuredAt = sample.timestamp.toEpochMilli()
                    )
                }
            } else {
                val level = dp.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)
                if (level == null || level <= 0f) emptyList()
                else listOf(
                    GlucoseRecord(
                        id         = "${dp.uid}-${dp.startTime.toEpochMilli()}",
                        value      = mgdlFromMmol(level),
                        timing     = timing,
                        measuredAt = dp.startTime.toEpochMilli()
                    )
                )
            }
        }.sortedByDescending { it.measuredAt }
    }

    /**
     * 심박수: DataTypes.HEART_RATE read 결과 → List&lt;HeartRateSample&gt;.
     *
     * 각 HealthDataPoint 는 SERIES_DATA Field (시계열 다중 샘플) 또는 단일 HEART_RATE Field 중
     * 한 형태로 데이터를 담는다. SERIES_DATA 가 있으면 펼치고, 없으면 평균값을 1개 샘플로 사용.
     */
    fun toHeartRateSamples(dataPoints: List<HealthDataPoint>): List<HeartRateSample> {
        if (dataPoints.isEmpty()) return emptyList()
        return dataPoints.flatMap { dp ->
            val series: List<SdkHeartRate> =
                dp.getValue(DataType.HeartRateType.SERIES_DATA) ?: emptyList()
            if (series.isNotEmpty()) {
                series.map { s ->
                    HeartRateSample(
                        timestamp = s.startTime.toEpochMilli(),
                        bpm       = s.heartRate.toInt()
                    )
                }
            } else {
                val avg = dp.getValue(DataType.HeartRateType.HEART_RATE)
                if (avg == null || avg <= 0f) emptyList()
                else listOf(
                    HeartRateSample(
                        timestamp = dp.startTime.toEpochMilli(),
                        bpm       = avg.toInt()
                    )
                )
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun koreanMealTiming(
        status: DataType.BloodGlucoseType.MealStatus?
    ): MealTiming = when (status) {
        DataType.BloodGlucoseType.MealStatus.FASTING            -> MealTiming.FASTING
        DataType.BloodGlucoseType.MealStatus.BEFORE_BREAKFAST,
        DataType.BloodGlucoseType.MealStatus.BEFORE_LUNCH,
        DataType.BloodGlucoseType.MealStatus.BEFORE_DINNER,
        DataType.BloodGlucoseType.MealStatus.BEFORE_MEAL        -> MealTiming.PRE_MEAL
        DataType.BloodGlucoseType.MealStatus.AFTER_BREAKFAST,
        DataType.BloodGlucoseType.MealStatus.AFTER_LUNCH,
        DataType.BloodGlucoseType.MealStatus.AFTER_DINNER,
        DataType.BloodGlucoseType.MealStatus.AFTER_MEAL,
        DataType.BloodGlucoseType.MealStatus.AFTER_SNACK        -> MealTiming.POST_MEAL_2H
        DataType.BloodGlucoseType.MealStatus.BEFORE_SLEEP,
        DataType.BloodGlucoseType.MealStatus.AFTER_BED_TIME     -> MealTiming.BEFORE_SLEEP
        null,
        DataType.BloodGlucoseType.MealStatus.GENERAL,
        DataType.BloodGlucoseType.MealStatus.UNDEFINED          -> MealTiming.OTHER
    }

    // ── 운동 종류 한글화 ─────────────────────────────────────────────────────

    private fun koreanExerciseTypeName(
        type: DataType.ExerciseType.PredefinedExerciseType?
    ): String = when (type) {
        DataType.ExerciseType.PredefinedExerciseType.WALKING            -> "걷기"
        DataType.ExerciseType.PredefinedExerciseType.RUNNING,
        DataType.ExerciseType.PredefinedExerciseType.TRACK_RUNNING,
        DataType.ExerciseType.PredefinedExerciseType.TREADMILL          -> "달리기"
        DataType.ExerciseType.PredefinedExerciseType.BIKING,
        DataType.ExerciseType.PredefinedExerciseType.STATIONARY_BIKING,
        DataType.ExerciseType.PredefinedExerciseType.MOUNTAIN_BIKING    -> "자전거"
        DataType.ExerciseType.PredefinedExerciseType.POOL_SWIMMING,
        DataType.ExerciseType.PredefinedExerciseType.OPEN_WATER_SWIMMING -> "수영"
        DataType.ExerciseType.PredefinedExerciseType.WEIGHT_MACHINE,
        DataType.ExerciseType.PredefinedExerciseType.BENCH_PRESS,
        DataType.ExerciseType.PredefinedExerciseType.SQUATS,
        DataType.ExerciseType.PredefinedExerciseType.DEADLIFTS,
        DataType.ExerciseType.PredefinedExerciseType.LUNGES             -> "근력 운동"
        DataType.ExerciseType.PredefinedExerciseType.YOGA,
        DataType.ExerciseType.PredefinedExerciseType.PILATES,
        DataType.ExerciseType.PredefinedExerciseType.STRETCHING         -> "요가"
        DataType.ExerciseType.PredefinedExerciseType.HIKING,
        DataType.ExerciseType.PredefinedExerciseType.BACKPACKING        -> "등산"
        DataType.ExerciseType.PredefinedExerciseType.STAIR_CLIMBING,
        DataType.ExerciseType.PredefinedExerciseType.STAIR_CLIMBING_MACHINE,
        DataType.ExerciseType.PredefinedExerciseType.STEP_MACHINE       -> "계단 오르기"
        null,
        DataType.ExerciseType.PredefinedExerciseType.UNDEFINED,
        DataType.ExerciseType.PredefinedExerciseType.OTHER              -> "기타 운동"
        else -> type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    // ── 식사 종류 한글화 ─────────────────────────────────────────────────────

    private fun koreanMealTypeName(
        type: DataType.NutritionType.MealType?
    ): String = when (type) {
        DataType.NutritionType.MealType.BREAKFAST       -> "아침"
        DataType.NutritionType.MealType.LUNCH           -> "점심"
        DataType.NutritionType.MealType.DINNER          -> "저녁"
        DataType.NutritionType.MealType.MORNING_SNACK,
        DataType.NutritionType.MealType.AFTERNOON_SNACK,
        DataType.NutritionType.MealType.EVENING_SNACK   -> "간식"
        null,
        DataType.NutritionType.MealType.UNDEFINED       -> "기타"
    }
}
