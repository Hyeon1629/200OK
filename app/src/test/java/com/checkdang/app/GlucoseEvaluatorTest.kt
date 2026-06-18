package com.checkdang.app

import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus
import com.checkdang.app.util.MealTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class GlucoseEvaluatorTest {

    @Test
    fun `식후 2시간 80 정상`() {
        assertEquals(GlucoseStatus.NORMAL, GlucoseEvaluator.evaluate(80, MealTiming.POST_MEAL_2H))
    }

    @Test
    fun `식후 2시간 150 주의`() {
        assertEquals(GlucoseStatus.WARNING, GlucoseEvaluator.evaluate(150, MealTiming.POST_MEAL_2H))
    }

    @Test
    fun `식후 2시간 220 위험`() {
        assertEquals(GlucoseStatus.DANGER, GlucoseEvaluator.evaluate(220, MealTiming.POST_MEAL_2H))
    }

    @Test
    fun `저혈당 70 미만 항상 위험`() {
        assertEquals(GlucoseStatus.DANGER, GlucoseEvaluator.evaluate(65, MealTiming.FASTING))
    }

    @Test
    fun `공복 99 정상`() {
        assertEquals(GlucoseStatus.NORMAL, GlucoseEvaluator.evaluate(99, MealTiming.FASTING))
    }

    @Test
    fun `공복 110 주의`() {
        assertEquals(GlucoseStatus.WARNING, GlucoseEvaluator.evaluate(110, MealTiming.FASTING))
    }

    @Test
    fun `공복 130 위험`() {
        assertEquals(GlucoseStatus.DANGER, GlucoseEvaluator.evaluate(130, MealTiming.FASTING))
    }

    @Test
    fun `getStatusLabel 정상`() {
        assertEquals("정상", GlucoseEvaluator.getStatusLabel(GlucoseStatus.NORMAL))
    }
}
