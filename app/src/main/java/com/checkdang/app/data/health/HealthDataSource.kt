package com.checkdang.app.data.health

import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary

interface HealthDataSource {
    fun isConnected(): Boolean
    suspend fun getExerciseSummary(): ExerciseSummary?
    suspend fun getMealSummary(): MealSummary?
    suspend fun getSleepSummary(): SleepSummary?
    suspend fun getWeeklyExerciseMinutes(): List<Int>
    suspend fun getWeeklySleepHours(): List<Float>
    suspend fun getLifestyleSummary(): LifestyleSummary?
}
