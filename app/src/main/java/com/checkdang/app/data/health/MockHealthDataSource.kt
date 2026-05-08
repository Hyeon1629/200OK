package com.checkdang.app.data.health

import com.checkdang.app.data.model.ExerciseSession
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealItem
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary

object MockHealthDataSource : HealthDataSource {

    override fun isConnected(): Boolean = true

    override suspend fun getExerciseSummary() = ExerciseSummary(
        totalMinutes  = 45,
        goalMinutes   = 60,
        totalCalories = 320,
        sessions = listOf(
            ExerciseSession("걷기",    30, 180, "오전 7:00"),
            ExerciseSession("스트레칭", 15, 140, "오후 7:30")
        )
    )

    override suspend fun getMealSummary() = MealSummary(
        totalKcal = 1640,
        goalKcal  = 2000,
        carbsG    = 210,
        proteinG  = 85,
        fatG      = 52,
        meals = listOf(
            MealItem("아침", "오트밀 + 바나나",      380, "오전 8:00"),
            MealItem("점심", "현미밥 + 닭가슴살",    620, "오후 12:30"),
            MealItem("저녁", "샐러드 + 두부",        480, "오후 6:45"),
            MealItem("간식", "아몬드 + 그릭요거트",  160, "오후 3:30")
        )
    )

    override suspend fun getSleepSummary() = SleepSummary(
        totalHours  = 7.2f,
        efficiency  = 88,
        deepHours   = 1.8f,
        lightHours  = 3.6f,
        remHours    = 1.8f,
        bedtime     = "23:45",
        wakeTime    = "06:57"
    )

    override suspend fun getWeeklyExerciseMinutes(): List<Int> =
        listOf(30, 0, 45, 60, 20, 0, 45)

    override suspend fun getWeeklySleepHours(): List<Float> =
        listOf(6.5f, 7.0f, 8.2f, 6.8f, 7.5f, 9.0f, 7.2f)

    override suspend fun getLifestyleSummary(): LifestyleSummary {
        val ex    = getExerciseSummary()
        val meal  = getMealSummary()
        val sleep = getSleepSummary()
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
