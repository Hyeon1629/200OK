package com.checkdang.app.data.model

import com.checkdang.app.util.MealTiming

data class GlucoseSummary(
    val latestValue: Int,
    val timing: MealTiming,
    val measuredAt: String,     // "오늘 14:32"
    val todayCount: Int,
    val todayAverage: Int
)
