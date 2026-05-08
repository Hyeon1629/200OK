package com.checkdang.app.data.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.checkdang.app.data.model.ExerciseSession
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealItem
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Android Health Connect 연동 구현체.
 *
 * 삼성 헬스 데이터 수신 조건:
 *   - Galaxy 기기 + Samsung Health 설치
 *   - Samsung Health → 설정 → Health Platform → "Health Connect와 동기화" 활성화 (One UI 6.0+)
 *   - 앱에서 Health Connect 읽기 권한 허가
 *
 * 지원 데이터: 운동(ExerciseSessionRecord), 수면(SleepSessionRecord), 식사(NutritionRecord)
 */
class HealthConnectDataSource(private val client: HealthConnectClient) : HealthDataSource {

    override fun isConnected(): Boolean = true

    override suspend fun getExerciseSummary(): ExerciseSummary? {
        val (start, end) = todayRange()
        val records = readSafe(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end))
        ) ?: return null
        if (records.isEmpty()) return null

        val sessions = records.map { r ->
            val durationMin = ((r.endTime.epochSecond - r.startTime.epochSecond) / 60).toInt()
            ExerciseSession(
                type        = exerciseTypeName(r.exerciseType),
                durationMin = durationMin,
                calories    = 0,  // TotalCaloriesBurnedRecord로 별도 집계 가능
                startedAt   = formatTime(r.startTime)
            )
        }
        return ExerciseSummary(
            totalMinutes  = sessions.sumOf { it.durationMin },
            goalMinutes   = 60,
            totalCalories = 0,
            sessions      = sessions
        )
    }

    override suspend fun getMealSummary(): MealSummary? {
        val (start, end) = todayRange()
        val records = readSafe(
            ReadRecordsRequest(NutritionRecord::class, TimeRangeFilter.between(start, end))
        ) ?: return null
        if (records.isEmpty()) return null

        var totalKcal  = 0
        var totalCarbs = 0.0
        var totalPro   = 0.0
        var totalFat   = 0.0
        val meals = records.map { r ->
            val kcal = r.energy?.inKilocalories?.toInt() ?: 0
            totalKcal  += kcal
            totalCarbs += r.totalCarbohydrate?.inGrams ?: 0.0
            totalPro   += r.protein?.inGrams           ?: 0.0
            totalFat   += r.totalFat?.inGrams          ?: 0.0
            MealItem(
                type  = mealTypeName(r.mealType),
                name  = r.name ?: "기록된 식사",
                kcal  = kcal,
                time  = formatTime(r.startTime)
            )
        }
        return MealSummary(
            totalKcal = totalKcal,
            goalKcal  = 2000,
            carbsG    = totalCarbs.toInt(),
            proteinG  = totalPro.toInt(),
            fatG      = totalFat.toInt(),
            meals     = meals
        )
    }

    override suspend fun getSleepSummary(): SleepSummary? {
        val zone  = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 어젯밤 18:00 ~ 오늘 12:00 범위로 수면 조회
        val start = today.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val end   = today.atTime(12, 0).atZone(zone).toInstant()

        val records = readSafe(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end))
        ) ?: return null
        val latest = records.maxByOrNull { it.startTime } ?: return null

        val totalHours = (latest.endTime.epochSecond - latest.startTime.epochSecond) / 3600f

        var deepHours  = 0f
        var lightHours = 0f
        var remHours   = 0f
        latest.stages.forEach { stage ->
            val h = (stage.endTime.epochSecond - stage.startTime.epochSecond) / 3600f
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_DEEP  -> deepHours  += h
                SleepSessionRecord.STAGE_TYPE_LIGHT -> lightHours += h
                SleepSessionRecord.STAGE_TYPE_REM   -> remHours   += h
            }
        }
        // 단계 데이터 없으면 일반적인 비율로 추정
        if (deepHours == 0f && lightHours == 0f && remHours == 0f) {
            deepHours  = totalHours * 0.20f
            lightHours = totalHours * 0.55f
            remHours   = totalHours * 0.25f
        }

        return SleepSummary(
            totalHours  = totalHours,
            efficiency  = 85,  // Health Connect에 효율 지표 없음 — 기본값
            deepHours   = deepHours,
            lightHours  = lightHours,
            remHours    = remHours,
            bedtime     = formatTime(latest.startTime),
            wakeTime    = formatTime(latest.endTime)
        )
    }

    override suspend fun getWeeklyExerciseMinutes(): List<Int> {
        val zone = ZoneId.systemDefault()
        return (6 downTo 0).map { ago ->
            val day   = LocalDate.now(zone).minusDays(ago.toLong())
            val start = day.atStartOfDay(zone).toInstant()
            val end   = day.plusDays(1).atStartOfDay(zone).toInstant()
            readSafe(
                ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end))
            )?.sumOf { ((it.endTime.epochSecond - it.startTime.epochSecond) / 60).toInt() } ?: 0
        }
    }

    override suspend fun getWeeklySleepHours(): List<Float> {
        val zone = ZoneId.systemDefault()
        return (6 downTo 0).map { ago ->
            val day   = LocalDate.now(zone).minusDays(ago.toLong())
            val start = day.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val end   = day.atTime(12, 0).atZone(zone).toInstant()
            readSafe(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end))
            )?.sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 3600.0 }?.toFloat() ?: 0f
        }
    }

    override suspend fun getLifestyleSummary(): LifestyleSummary? {
        val ex    = getExerciseSummary() ?: return null
        val meal  = getMealSummary()     ?: return null
        val sleep = getSleepSummary()    ?: return null
        return LifestyleSummary(
            exerciseMinutes     = ex.totalMinutes,
            exerciseGoalMinutes = ex.goalMinutes,
            mealKcal            = meal.totalKcal,
            mealGoalKcal        = meal.goalKcal,
            sleepHours          = sleep.totalHours,
            sleepEfficiency     = sleep.efficiency
        )
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────

    private suspend fun <T : Record> readSafe(request: ReadRecordsRequest<T>): List<T>? =
        runCatching { client.readRecords(request).records }.getOrNull()

    private fun todayRange(): Pair<Instant, Instant> {
        val zone  = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return today.atStartOfDay(zone).toInstant() to today.plusDays(1).atStartOfDay(zone).toInstant()
    }

    private fun formatTime(instant: Instant): String {
        val zdt  = instant.atZone(ZoneId.systemDefault())
        val mer  = if (zdt.hour < 12) "오전" else "오후"
        val disp = if (zdt.hour == 0 || zdt.hour == 12) 12 else zdt.hour % 12
        return "$mer $disp:%02d".format(zdt.minute)
    }

    private fun exerciseTypeName(type: Int) = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING            -> "걷기"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING            -> "달리기"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING             -> "자전거"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL      -> "수영"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING  -> "근력 운동"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA               -> "요가"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING             -> "등산"
        else                                                    -> "기타 운동"
    }

    private fun mealTypeName(type: Int) = when (type) {
        1    -> "아침"   // MEAL_TYPE_BREAKFAST
        2    -> "점심"   // MEAL_TYPE_LUNCH
        3    -> "저녁"   // MEAL_TYPE_DINNER
        4    -> "간식"   // MEAL_TYPE_SNACK
        else -> "기타"
    }
}
