package com.checkdang.app.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.checkdang.app.R

enum class GlucoseStatus { NORMAL, WARNING, DANGER }

enum class MealTiming(val label: String) {
    FASTING("공복"),
    PRE_MEAL("식전"),
    POST_MEAL_30M("식후 30분"),
    POST_MEAL_1H("식후 1시간"),
    POST_MEAL_2H("식후 2시간"),
    BEFORE_SLEEP("취침 전"),
    OTHER("임의")
}

object GlucoseEvaluator {

    fun evaluate(value: Int, timing: MealTiming): GlucoseStatus {
        if (value < 70) return GlucoseStatus.DANGER

        return when (timing) {
            MealTiming.FASTING -> when {
                value <= 99  -> GlucoseStatus.NORMAL
                value <= 125 -> GlucoseStatus.WARNING
                else         -> GlucoseStatus.DANGER
            }
            MealTiming.POST_MEAL_2H -> when {
                value <= 139 -> GlucoseStatus.NORMAL
                value <= 199 -> GlucoseStatus.WARNING
                else         -> GlucoseStatus.DANGER
            }
            else -> when {  // PRE_MEAL, POST_MEAL_30M, POST_MEAL_1H, BEFORE_SLEEP, OTHER
                value <= 139 -> GlucoseStatus.NORMAL
                value <= 199 -> GlucoseStatus.WARNING
                else         -> GlucoseStatus.DANGER
            }
        }
    }

    fun getColor(status: GlucoseStatus, context: Context): Int {
        val colorRes = when (status) {
            GlucoseStatus.NORMAL  -> R.color.status_normal
            GlucoseStatus.WARNING -> R.color.status_warning
            GlucoseStatus.DANGER  -> R.color.status_danger
        }
        return ContextCompat.getColor(context, colorRes)
    }

    fun getStatusLabel(status: GlucoseStatus): String = when (status) {
        GlucoseStatus.NORMAL  -> "정상"
        GlucoseStatus.WARNING -> "주의"
        GlucoseStatus.DANGER  -> "위험"
    }
}
