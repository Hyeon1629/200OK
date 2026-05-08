package com.checkdang.app.data.health

import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary

/**
 * 활성 HealthDataSource를 관리하는 싱글톤.
 *
 * 기본값: MockHealthDataSource (개발/오프라인)
 * 권한 허가 후: switchToHealthConnect() 호출 → 삼성 헬스 실제 데이터 사용
 */
object HealthRepository {

    private var source: HealthDataSource = MockHealthDataSource

    /** Health Connect 권한 허가 완료 후 호출 */
    fun switchToHealthConnect(healthConnectSource: HealthConnectDataSource) {
        source = healthConnectSource
    }

    /** 개발/테스트: Mock으로 되돌리기 */
    fun resetToMock() {
        source = MockHealthDataSource
    }

    fun isConnectedToHealthConnect(): Boolean =
        source is HealthConnectDataSource

    suspend fun getExerciseSummary(): ExerciseSummary?   = source.getExerciseSummary()
    suspend fun getMealSummary(): MealSummary?            = source.getMealSummary()
    suspend fun getSleepSummary(): SleepSummary?         = source.getSleepSummary()
    suspend fun getWeeklyExerciseMinutes(): List<Int>    = source.getWeeklyExerciseMinutes()
    suspend fun getWeeklySleepHours(): List<Float>       = source.getWeeklySleepHours()
    suspend fun getLifestyleSummary(): LifestyleSummary? = source.getLifestyleSummary()
}
