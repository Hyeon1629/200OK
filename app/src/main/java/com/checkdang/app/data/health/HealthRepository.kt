package com.checkdang.app.data.health

import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary

/**
 * 활성 HealthDataSource를 관리하는 싱글톤.
 *
 * 기본값: MockHealthDataSource (개발/오프라인)
 * 권한 허가 후 둘 중 하나만 활성화:
 *  - switchToHealthConnect() → Health Connect 경유 데이터 (현 기본 경로)
 *  - switchToSamsungHealth() → Samsung Health Data SDK 직접 호출 (개발자 모드)
 *
 * 두 소스는 동시에 활성화되지 않으므로 SDK 호출 충돌 가능성이 없다.
 */
object HealthRepository {

    private var source: HealthDataSource = MockHealthDataSource

    /** Health Connect 권한 허가 완료 후 호출 */
    fun switchToHealthConnect(healthConnectSource: HealthConnectDataSource) {
        source = healthConnectSource
    }

    /** Samsung Health Data SDK 권한 허가 완료 후 호출 */
    fun switchToSamsungHealth(samsungSource: SamsungHealthDataSource) {
        source = samsungSource
    }

    /** 개발/테스트: Mock으로 되돌리기 */
    fun resetToMock() {
        source = MockHealthDataSource
    }

    fun isConnectedToHealthConnect(): Boolean =
        source is HealthConnectDataSource

    fun isConnectedToSamsungHealth(): Boolean =
        source is SamsungHealthDataSource

    suspend fun getExerciseSummary(): ExerciseSummary?   = source.getExerciseSummary()
    suspend fun getMealSummary(): MealSummary?            = source.getMealSummary()
    suspend fun getSleepSummary(): SleepSummary?         = source.getSleepSummary()
    suspend fun getWeeklyExerciseMinutes(): List<Int>    = source.getWeeklyExerciseMinutes()
    suspend fun getWeeklySleepHours(): List<Float>       = source.getWeeklySleepHours()
    suspend fun getLifestyleSummary(): LifestyleSummary? = source.getLifestyleSummary()

    /**
     * 외부 헬스 소스에서 자동 측정된 최근 [days] 일치 혈당 기록.
     * Mock / Health Connect 활성 시 빈 리스트, Samsung Health 활성 시 실 데이터.
     */
    suspend fun getBloodGlucoseRecords(days: Int): List<GlucoseRecord> =
        source.getBloodGlucoseRecords(days)
}
