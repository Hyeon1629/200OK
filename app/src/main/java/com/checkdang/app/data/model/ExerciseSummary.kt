package com.checkdang.app.data.model

data class ExerciseSummary(
    val totalMinutes: Int,
    val goalMinutes: Int,
    val totalCalories: Int,
    val sessions: List<ExerciseSession>
)

data class ExerciseSession(
    val type: String,       // "걷기", "달리기", "자전거"
    val durationMin: Int,
    val calories: Int,
    val startedAt: String   // "오전 7:30"
)
