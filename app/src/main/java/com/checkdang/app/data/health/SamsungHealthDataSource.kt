package com.checkdang.app.data.health

import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.HeartRateSample
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import com.checkdang.app.data.samsunghealth.ConnectionState
import com.checkdang.app.data.samsunghealth.SamsungHealthRepository
import java.time.LocalDate

/**
 * Samsung Health Data SDK 어댑터.
 *
 * 통합 [HealthDataSource] 인터페이스 뒤로 [SamsungHealthRepository] 의 SDK 호출을 위임한다.
 * 두 헬스 소스(Samsung Health Data SDK, Health Connect)는 [HealthRepository] 가 런타임에
 * 하나만 활성화하므로 동시 호출이 발생하지 않는다.
 *
 * 데이터 read 가 아직 Phase 2-foundation 단계라서 운동/식사/수면 항목은 null 을 반환한다.
 * UI 는 기존 EmptyState 처리를 그대로 따른다.
 */
class SamsungHealthDataSource(
    private val repo: SamsungHealthRepository
) : HealthDataSource {

    override fun isConnected(): Boolean =
        repo.state.value is ConnectionState.Connected

    override suspend fun getExerciseSummary(): ExerciseSummary? =
        repo.readExercise(LocalDate.now())

    override suspend fun getMealSummary(): MealSummary? =
        repo.readMeal(LocalDate.now())

    override suspend fun getSleepSummary(): SleepSummary? =
        repo.readSleep(LocalDate.now())

    override suspend fun getWeeklyExerciseMinutes(): List<Int> {
        val today = LocalDate.now()
        return (6 downTo 0).map { ago ->
            repo.readExercise(today.minusDays(ago.toLong()))?.totalMinutes ?: 0
        }
    }

    override suspend fun getWeeklySleepHours(): List<Float> {
        val today = LocalDate.now()
        return (6 downTo 0).map { ago ->
            repo.readSleep(today.minusDays(ago.toLong()))?.totalHours ?: 0f
        }
    }

    override suspend fun getBloodGlucoseRecords(days: Int): List<GlucoseRecord> {
        val today = LocalDate.now()
        return repo.readBloodGlucoseRange(
            start        = today.minusDays((days - 1).toLong()),
            endExclusive = today.plusDays(1)
        )
    }

    override suspend fun getStepCount(date: LocalDate): Int? =
        repo.readSteps(date)

    override suspend fun getHeartRates(date: LocalDate): List<HeartRateSample> =
        repo.readHeartRate(date)

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
}
