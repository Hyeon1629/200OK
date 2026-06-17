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
    val time: String,
    val carbsG: Int = 0,   // 레코드별 탄수화물(g). 혈당 예측 carbs 피처용으로 서버 전송.
)
