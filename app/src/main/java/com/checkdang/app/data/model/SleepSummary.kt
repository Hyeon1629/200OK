package com.checkdang.app.data.model

data class SleepSummary(
    val totalHours: Float,
    val efficiency: Int,    // %
    val deepHours: Float,
    val lightHours: Float,
    val remHours: Float,
    val bedtime: String,    // "23:42"
    val wakeTime: String    // "06:54"
)
