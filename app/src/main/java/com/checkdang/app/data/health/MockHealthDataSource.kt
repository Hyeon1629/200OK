package com.checkdang.app.data.health

import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary

/**
 * 실제 헬스 소스(Health Connect / Samsung Health) 연결 전 기본 소스.
 *
 * 라이프스타일(운동·식사·수면) 데이터는 더미를 반환하지 않는다 — 미연결 상태에서는
 * null/빈 리스트를 내려 UI 가 EmptyState 를 표시하도록 한다. (가짜 데이터가 화면·백엔드로
 * 유출되는 것을 방지)
 */
object MockHealthDataSource : HealthDataSource {

    override fun isConnected(): Boolean = true

    override suspend fun getExerciseSummary(): ExerciseSummary? = null

    override suspend fun getMealSummary(): MealSummary? = null

    override suspend fun getSleepSummary(): SleepSummary? = null

    override suspend fun getWeeklyExerciseMinutes(): List<Int> = emptyList()

    override suspend fun getWeeklySleepHours(): List<Float> = emptyList()

    override suspend fun getLifestyleSummary(): LifestyleSummary? = null
}
