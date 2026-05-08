package com.checkdang.app.data.health

import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary

/**
 * Samsung Health SDK 직접 연동 stub (미래 구현 예정).
 *
 * 현재는 Android Health Connect(HealthConnectDataSource)가 실질적인 삼성 헬스 연동 역할을 한다.
 * Samsung Health SDK 직접 연동이 필요한 경우(예: Health Connect 미지원 기능):
 *
 * 1. 삼성 개발자 포털에서 SDK 다운로드: developer.samsung.com/health
 * 2. app/libs/ 에 AAR 배치 후 build.gradle.kts에 추가
 *      implementation(fileTree("libs") { include("*.aar") })
 * 3. AndroidManifest.xml에 권한 선언
 *      <uses-permission android:name=
 *          "com.samsung.android.providers.context.permission.WRITE_USE_APP_FEATURE_SURVEY"/>
 * 4. SHealthManager.init(context)로 초기화
 * 5. HealthRepository.switchTo(SamsungHealthDataSource()) 호출
 *
 * 데이터 타입 매핑:
 *   운동 → HealthConstants.Exercise (EXERCISE_TYPE, DURATION, CALORIE, START_TIME)
 *   수면 → HealthConstants.Sleep + SleepStage (STAGE_TYPE: DEEP=4, LIGHT=1/2, REM=5)
 *   식사 → HealthConstants.Nutrition (CALORIE, CARBOHYDRATE, PROTEIN, FAT, MEAL_TYPE)
 */
class SamsungHealthDataSource : HealthDataSource {

    override fun isConnected(): Boolean {
        // TODO(samsung-health): SHealthManager.isFeatureEnabled(SHEALTH_FEATURE) && store.isConnected
        return false
    }

    override suspend fun getExerciseSummary(): ExerciseSummary? {
        // TODO(samsung-health): HealthConstants.Exercise.HEALTH_DATA_TYPE 쿼리
        return null
    }

    override suspend fun getMealSummary(): MealSummary? {
        // TODO(samsung-health): HealthConstants.Nutrition.HEALTH_DATA_TYPE 쿼리
        return null
    }

    override suspend fun getSleepSummary(): SleepSummary? {
        // TODO(samsung-health): HealthConstants.SleepStage.HEALTH_DATA_TYPE 쿼리
        return null
    }

    override suspend fun getWeeklyExerciseMinutes(): List<Int> = emptyList()

    override suspend fun getWeeklySleepHours(): List<Float> = emptyList()

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
