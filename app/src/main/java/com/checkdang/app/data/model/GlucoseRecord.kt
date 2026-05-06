package com.checkdang.app.data.model

import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus
import com.checkdang.app.util.MealTiming

data class GlucoseRecord(
    val id: String,
    val value: Int,
    val timing: MealTiming,
    val measuredAt: Long,       // epoch millis
    val memo: String? = null
) {
    val status: GlucoseStatus get() = GlucoseEvaluator.evaluate(value, timing)
}
