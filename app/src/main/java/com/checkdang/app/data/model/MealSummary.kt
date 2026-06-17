package com.checkdang.app.data.model

data class MealSummary(
    val totalKcal: Int,
    val goalKcal: Int,
    val carbsG: Int,
    val proteinG: Int,
    val fatG: Int,
    val meals: List<MealItem>
)

data class MealItem(
    val type: String,   // "아침", "점심", "저녁", "간식"
    val name: String,
    val kcal: Int,
    val time: String
)
