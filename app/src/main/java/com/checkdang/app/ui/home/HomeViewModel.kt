package com.checkdang.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.health.HealthRepository
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.GlucoseSummary
import com.checkdang.app.data.model.LifestyleSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 홈 "오늘 인슐린" 요약. */
data class InsulinDaySummary(
    val totalUnits: Float,
    val count: Int,
    val lastLabel: String?,   // "속효성 6U" 형태 (없으면 null)
)

class HomeViewModel : ViewModel() {

    // 혈당 요약 — 혈당 탭과 동일 소스(MockDataProvider.recordsFlow)에서 파생 → 입력 즉시 반영.
    val glucoseSummary: StateFlow<GlucoseSummary?> = MockDataProvider.recordsFlow
        .map { buildGlucoseSummary(it) }
        .stateIn(
            viewModelScope, SharingStarted.Eagerly,
            buildGlucoseSummary(MockDataProvider.recordsFlow.value)
        )

    // 7일 혈당(일별 평균) — 빈 날은 0f(차트에서 제외).
    val weeklyGlucose: StateFlow<List<Float>> = MockDataProvider.recordsFlow
        .map { buildWeekly(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 라이프스타일 요약 — 라이프스타일 탭과 동일 소스(HealthRepository) async 로드.
    private val _lifestyleSummary = MutableStateFlow<LifestyleSummary?>(null)
    val lifestyleSummary: StateFlow<LifestyleSummary?> = _lifestyleSummary.asStateFlow()

    // 오늘 인슐린 합계/횟수/최근 1건 — 입력 즉시 반영.
    val todayInsulin: StateFlow<InsulinDaySummary> = MockDataProvider.insulinRecordsFlow
        .map { records ->
            val start = startOfDay(0)
            val today = records.filter { it.injectedAt >= start }
            InsulinDaySummary(
                totalUnits = today.sumOf { it.units.toDouble() }.toFloat(),
                count      = today.size,
                lastLabel  = today.maxByOrNull { it.injectedAt }
                    ?.let { "${it.type.label} ${it.unitsLabel}U" }
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, InsulinDaySummary(0f, 0, null))

    init { loadLifestyle() }

    /**
     * 현재 HealthRepository 소스(Mock/HealthConnect/Samsung)로 라이프스타일 재로드. 홈 진입/재방문 시 호출.
     * 프리미엄(PAID) 구독자가 아니면 삼성 헬스 연동 자체가 제공되지 않으므로 0 값으로 고정.
     */
    fun loadLifestyle() {
        viewModelScope.launch {
            if (SessionHolder.tier != UserTier.PAID) {
                _lifestyleSummary.value = LifestyleSummary(
                    exerciseMinutes     = 0,
                    exerciseGoalMinutes = 60,
                    mealKcal            = 0,
                    mealGoalKcal        = 2000,
                    sleepHours          = 0f,
                    sleepEfficiency     = 0,
                )
                return@launch
            }
            val ex = HealthRepository.getExerciseSummary()
            val ml = HealthRepository.getMealSummary()
            val sl = HealthRepository.getSleepSummary()
            _lifestyleSummary.value =
                if (ex == null && ml == null && sl == null) null
                else LifestyleSummary(
                    exerciseMinutes     = ex?.totalMinutes ?: 0,
                    exerciseGoalMinutes = ex?.goalMinutes ?: 60,
                    mealKcal            = ml?.totalKcal ?: 0,
                    mealGoalKcal        = ml?.goalKcal ?: 2000,
                    sleepHours          = sl?.totalHours ?: 0f,
                    sleepEfficiency     = sl?.efficiency ?: 0,
                )
        }
    }

    private fun buildGlucoseSummary(records: List<GlucoseRecord>): GlucoseSummary? {
        val latest = records.maxByOrNull { it.measuredAt } ?: return null
        val start = startOfDay(0)
        val today = records.filter { it.measuredAt >= start }
        val avg = if (today.isEmpty()) latest.value else today.map { it.value }.average().toInt()
        return GlucoseSummary(
            latestValue  = latest.value,
            timing       = latest.timing,
            measuredAt   = labelFor(latest.measuredAt),
            todayCount   = today.size,
            todayAverage = avg,
        )
    }

    /** 최근 7일(6일 전 → 오늘) 일별 평균. 데이터 없는 날은 0f. */
    private fun buildWeekly(records: List<GlucoseRecord>): List<Float> {
        if (records.isEmpty()) return emptyList()
        return (6 downTo 0).map { ago ->
            val dayStart = startOfDay(-ago)
            val dayEnd   = startOfDay(-ago + 1)
            val dayRecs  = records.filter { it.measuredAt in dayStart until dayEnd }
            if (dayRecs.isEmpty()) 0f else dayRecs.map { it.value }.average().toFloat()
        }
    }

    private fun startOfDay(daysOffset: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, daysOffset)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun labelFor(ts: Long): String =
        if (ts >= startOfDay(0)) "오늘 ${SimpleDateFormat("HH:mm", Locale.KOREAN).format(Date(ts))}"
        else SimpleDateFormat("MM.dd HH:mm", Locale.KOREAN).format(Date(ts))
}
