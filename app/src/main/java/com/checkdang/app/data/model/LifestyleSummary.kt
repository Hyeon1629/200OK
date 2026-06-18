package com.checkdang.app.data.model

data class LifestyleSummary(
    val exerciseMinutes: Int,
    val exerciseGoalMinutes: Int,
    val mealKcal: Int,
    val mealGoalKcal: Int,
    val sleepHours: Float,
    val sleepEfficiency: Int    // 0~100%
)
