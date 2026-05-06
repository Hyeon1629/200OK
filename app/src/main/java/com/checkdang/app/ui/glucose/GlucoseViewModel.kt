package com.checkdang.app.ui.glucose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.GlucoseRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class WeeklyStats(val average: Int, val max: Int, val min: Int)

class GlucoseViewModel : ViewModel() {

    /** 전체 기록 (최신순) */
    val records: StateFlow<List<GlucoseRecord>> = MockDataProvider.recordsFlow

    /** 차트 기간 필터: 7 / 30 / 90일 */
    private val _filterDays = MutableStateFlow(7)
    val filterDays: StateFlow<Int> = _filterDays.asStateFlow()

    /** 필터 적용 기록 (오래된 순 — 차트 X축 용) */
    val filteredForChart: StateFlow<List<GlucoseRecord>> = combine(
        MockDataProvider.recordsFlow, _filterDays
    ) { recs, days ->
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        recs.filter { it.measuredAt >= cutoff }.sortedBy { it.measuredAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 이번 주 평균/최고/최저 */
    val weeklyStats: StateFlow<WeeklyStats> = MockDataProvider.recordsFlow
        .map { recs ->
            val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            val weekly = recs.filter { it.measuredAt >= cutoff }
            if (weekly.isEmpty()) WeeklyStats(0, 0, 0)
            else WeeklyStats(
                average = weekly.map { it.value }.average().toInt(),
                max     = weekly.maxOf { it.value },
                min     = weekly.minOf { it.value }
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeeklyStats(0, 0, 0))

    fun setFilter(days: Int) { _filterDays.value = days }
}
