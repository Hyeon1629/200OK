package com.checkdang.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GlucoseEvaluator.evaluate] 경계값 테스트.
 *
 * 알림(GlucoseAlertNotifier)·차트·홈 카드가 모두 이 판정에 의존하므로 경계값을 고정한다.
 * 기준(CLAUDE.md):
 *  - 저혈당: < 70 (모든 측정 유형, DANGER)
 *  - 공복:        정상 70–99 / 주의 100–125 / 위험 ≥126
 *  - 식후 2시간:  정상 70–139 / 주의 140–199 / 위험 ≥200
 *  - 그 외 측정:  식후 2시간과 동일 구간
 */
class GlucoseEvaluatorTest {

    // ── 저혈당: 측정 유형 무관 < 70 → DANGER ──────────────────────────────────
    @Test
    fun low_below70_isDanger_regardlessOfTiming() {
        for (timing in MealTiming.values()) {
            assertEquals("$timing 69", GlucoseStatus.DANGER, GlucoseEvaluator.evaluate(69, timing))
        }
        // 50(입력 하한 근처)도 저혈당
        assertEquals(GlucoseStatus.DANGER, GlucoseEvaluator.evaluate(50, MealTiming.FASTING))
    }

    // ── 공복(FASTING) 경계값 ──────────────────────────────────────────────────
    @Test
    fun fasting_boundaries() {
        assertEquals(GlucoseStatus.NORMAL,  GlucoseEvaluator.evaluate(70,  MealTiming.FASTING))
        assertEquals(GlucoseStatus.NORMAL,  GlucoseEvaluator.evaluate(99,  MealTiming.FASTING))
        assertEquals(GlucoseStatus.WARNING, GlucoseEvaluator.evaluate(100, MealTiming.FASTING))
        assertEquals(GlucoseStatus.WARNING, GlucoseEvaluator.evaluate(125, MealTiming.FASTING))
        assertEquals(GlucoseStatus.DANGER,  GlucoseEvaluator.evaluate(126, MealTiming.FASTING))
    }

    // ── 식후 2시간(POST_MEAL_2H) 경계값 ──────────────────────────────────────
    @Test
    fun postMeal2h_boundaries() {
        assertEquals(GlucoseStatus.NORMAL,  GlucoseEvaluator.evaluate(70,  MealTiming.POST_MEAL_2H))
        assertEquals(GlucoseStatus.NORMAL,  GlucoseEvaluator.evaluate(139, MealTiming.POST_MEAL_2H))
        assertEquals(GlucoseStatus.WARNING, GlucoseEvaluator.evaluate(140, MealTiming.POST_MEAL_2H))
        assertEquals(GlucoseStatus.WARNING, GlucoseEvaluator.evaluate(199, MealTiming.POST_MEAL_2H))
        assertEquals(GlucoseStatus.DANGER,  GlucoseEvaluator.evaluate(200, MealTiming.POST_MEAL_2H))
    }

    // ── 그 외 측정 유형은 식후 2시간 구간과 동일 ──────────────────────────────
    @Test
    fun otherTimings_useSameRangeAsPost2h() {
        for (timing in listOf(
            MealTiming.PRE_MEAL, MealTiming.POST_MEAL_30M,
            MealTiming.POST_MEAL_1H, MealTiming.BEFORE_SLEEP, MealTiming.OTHER,
        )) {
            assertEquals("$timing 139", GlucoseStatus.NORMAL,  GlucoseEvaluator.evaluate(139, timing))
            assertEquals("$timing 140", GlucoseStatus.WARNING, GlucoseEvaluator.evaluate(140, timing))
            assertEquals("$timing 200", GlucoseStatus.DANGER,  GlucoseEvaluator.evaluate(200, timing))
        }
    }
}
