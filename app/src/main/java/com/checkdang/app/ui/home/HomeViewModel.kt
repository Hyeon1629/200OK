package com.checkdang.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.GlucoseSummary
import com.checkdang.app.data.model.LifestyleSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/** 홈 "오늘 인슐린" 요약. */
data class InsulinDaySummary(
    val totalUnits: Float,
    val count: Int,
    val lastLabel: String?,   // "속효성 6U" 형태 (없으면 null)
)

class HomeViewModel : ViewModel() {

    private val _glucoseSummary = MutableStateFlow<GlucoseSummary?>(MockDataProvider.getGlucoseSummary())
    val glucoseSummary: StateFlow<GlucoseSummary?> = _glucoseSummary.asStateFlow()

    private val _lifestyleSummary = MutableStateFlow<LifestyleSummary?>(MockDataProvider.getLifestyleSummary())
    val lifestyleSummary: StateFlow<LifestyleSummary?> = _lifestyleSummary.asStateFlow()

    private val _weeklyGlucose = MutableStateFlow(MockDataProvider.getWeeklyGlucose())
    val weeklyGlucose: StateFlow<List<Float>> = _weeklyGlucose.asStateFlow()

    /** 오늘 주입한 인슐린 합계/횟수/최근 1건. 입력 즉시 반영되도록 flow 로 구독. */
    val todayInsulin: StateFlow<InsulinDaySummary> = MockDataProvider.insulinRecordsFlow
        .map { records ->
            val start = startOfToday()
            val today = records.filter { it.injectedAt >= start }
            InsulinDaySummary(
                totalUnits = today.sumOf { it.units.toDouble() }.toFloat(),
                count      = today.size,
                lastLabel  = today.maxByOrNull { it.injectedAt }
                    ?.let { "${it.type.label} ${it.unitsLabel}U" }
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, InsulinDaySummary(0f, 0, null))

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
