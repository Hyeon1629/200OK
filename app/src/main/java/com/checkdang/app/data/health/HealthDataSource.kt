package com.checkdang.app.data.health

import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.HeartRateSample
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import java.time.LocalDate

interface HealthDataSource {
    fun isConnected(): Boolean
    suspend fun getExerciseSummary(): ExerciseSummary?
    suspend fun getMealSummary(): MealSummary?
    suspend fun getSleepSummary(): SleepSummary?
    suspend fun getWeeklyExerciseMinutes(): List<Int>
    suspend fun getWeeklySleepHours(): List<Float>
    suspend fun getLifestyleSummary(): LifestyleSummary?

    /**
     * 최근 [days] 일치 혈당 기록. 사용자 직접 입력 기록은 별도 보관소(MockDataProvider)에서
     * 관리하며, 본 메서드는 외부 헬스 소스(Samsung Health 등) 에서 가져온 자동 측정 기록만 반환.
     *
     * 기본 구현: 빈 리스트 (Mock/Health Connect — 자동 측정 데이터 미지원).
     */
    suspend fun getBloodGlucoseRecords(days: Int): List<GlucoseRecord> = emptyList()

    /**
     * 일별 총 걸음 수. Mock/HealthConnect 는 미지원 → null.
     * Samsung Health Data SDK 활성 시에만 실 데이터 반환.
     */
    suspend fun getStepCount(date: LocalDate): Int? = null

    /**
     * 일별 심박수 시계열 샘플. Mock/HealthConnect 는 미지원 → 빈 리스트.
     */
    suspend fun getHeartRates(date: LocalDate): List<HeartRateSample> = emptyList()
}
