package com.checkdang.app.ui.glucose.prediction

import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus
import com.checkdang.app.util.MealTiming
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 지금까지의 혈당 기록을 분석해 **앞으로의 혈당을 예측**하는 컴포넌트.
 *
 * IMPORTANT: 실제 AI/ML 모델은 **미구현**. 현재는 최근 기록의 선형 추세(최소제곱 회귀)를
 * 그대로 외삽(extrapolation)하는 단순 통계 자리표시자다. UI·데이터 흐름 검증용 골격이며,
 * 실제 모델 연동 시 [predict] 시그니처를 유지한 채 내부만 교체하면 된다.
 * (Health 연동의 `HealthDataSource` 교체 패턴과 동일한 의도)
 */
object GlucosePredictor {

    private const val MIN_RECORDS = 4
    private const val WINDOW_DAYS = 14
    private const val FUTURE_POINTS = 3
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val STABLE_SLOPE = 1.0   // |기울기|(mg/dL/day) 이하면 '안정'으로 판정

    private val POINT_LABELS = listOf("내일", "모레", "3일 후")

    /** 미래 예측 지점 한 개. */
    data class Point(val label: String, val value: Int, val status: GlucoseStatus)

    enum class Trend(val label: String, val arrow: String) {
        RISING("상승세", "▲"),
        FALLING("하락세", "▼"),
        STABLE("안정", "▬"),
    }

    data class Result(
        val points: List<Point>,    // 향후 예측 지점 (가까운 순)
        val trend: Trend,
        val headline: String,       // 사용자에게 보여줄 한 줄 알림
        val detail: String,         // 보조 설명
        val confidence: Int,        // 0..100 (Mock)
    )

    /** 기록이 부족하거나 추세를 낼 수 없으면 null. */
    fun predict(records: List<GlucoseRecord>): Result? {
        if (records.size < MIN_RECORDS) return null
        val now = System.currentTimeMillis()
        val window = records
            .filter { it.measuredAt >= now - WINDOW_DAYS * DAY_MS }
            .sortedBy { it.measuredAt }
        if (window.size < MIN_RECORDS) return null

        val t0 = window.first().measuredAt
        val xs = window.map { (it.measuredAt - t0).toDouble() / DAY_MS }   // 일 단위
        val ys = window.map { it.value.toDouble() }
        if (xs.last() - xs.first() < 0.5) return null   // 측정 기간이 반나절 미만이면 추세 무의미

        val (slope, intercept) = linearFit(xs, ys)
        val lastX = xs.last()

        val points = (1..FUTURE_POINTS).map { k ->
            val v = (intercept + slope * (lastX + k)).roundToInt().coerceIn(50, 350)
            Point(POINT_LABELS[k - 1], v, GlucoseEvaluator.evaluate(v, MealTiming.FASTING))
        }

        val trend = when {
            slope > STABLE_SLOPE -> Trend.RISING
            slope < -STABLE_SLOPE -> Trend.FALLING
            else -> Trend.STABLE
        }
        val avgFuture = points.map { it.value }.average().roundToInt()
        val risk = GlucoseEvaluator.evaluate(avgFuture, MealTiming.FASTING)

        return Result(
            points = points,
            trend = trend,
            headline = buildHeadline(trend, risk, avgFuture),
            detail = "최근 ${window.size}건 기록의 추세를 바탕으로 한 향후 ${FUTURE_POINTS}일 예측이에요.",
            confidence = confidenceOf(xs, ys, slope, intercept, window.size),
        )
    }

    private fun buildHeadline(trend: Trend, risk: GlucoseStatus, avg: Int): String = when (trend) {
        Trend.RISING -> when (risk) {
            GlucoseStatus.NORMAL -> "혈당이 조금씩 오르는 추세예요. 식사·운동 패턴을 점검해보세요."
            else -> "혈당이 오르는 추세로, 며칠 내 ${GlucoseEvaluator.getStatusLabel(risk)} 범위(예상 평균 $avg)가 예상돼요."
        }
        Trend.FALLING -> when (risk) {
            GlucoseStatus.DANGER -> "혈당이 빠르게 내려가는 추세예요. 저혈당에 주의하세요."
            else -> "혈당이 안정적으로 내려가는 추세예요. 좋은 흐름이에요."
        }
        Trend.STABLE -> when (risk) {
            GlucoseStatus.NORMAL -> "혈당이 안정적으로 유지되고 있어요 (예상 평균 $avg)."
            else -> "혈당이 ${GlucoseEvaluator.getStatusLabel(risk)} 범위에서 유지되는 추세예요 (예상 평균 $avg)."
        }
    }

    /** 최소제곱 직선 적합 → (기울기, 절편). */
    private fun linearFit(xs: List<Double>, ys: List<Double>): Pair<Double, Double> {
        val n = xs.size
        val sx = xs.sum()
        val sy = ys.sum()
        val sxx = xs.sumOf { it * it }
        val sxy = xs.indices.sumOf { xs[it] * ys[it] }
        val denom = n * sxx - sx * sx
        if (denom == 0.0) return 0.0 to (sy / n)
        val slope = (n * sxy - sx * sy) / denom
        val intercept = (sy - slope * sx) / n
        return slope to intercept
    }

    /** 적합도(R²) + 데이터 양으로 Mock 신뢰도 산출 (40..95). */
    private fun confidenceOf(
        xs: List<Double>, ys: List<Double>, slope: Double, intercept: Double, n: Int
    ): Int {
        val meanY = ys.average()
        val ssTot = ys.sumOf { (it - meanY).pow(2) }
        val ssRes = xs.indices.sumOf { (ys[it] - (intercept + slope * xs[it])).pow(2) }
        val r2 = if (ssTot == 0.0) 0.0 else (1 - ssRes / ssTot).coerceIn(0.0, 1.0)
        val dataBonus = (n.coerceAtMost(20) / 20.0) * 10   // 기록 많을수록 최대 +10
        return (45 + r2 * 45 + dataBonus).roundToInt().coerceIn(40, 95)
    }
}
