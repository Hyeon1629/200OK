package com.checkdang.app.ui.glucose

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.health.HealthRepository
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.remote.HealthSyncApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeeklyStats(val average: Int, val max: Int, val min: Int)

class GlucoseViewModel : ViewModel() {

    /** 외부 헬스 소스(Samsung Health)에서 가져온 자동 측정 혈당. 활성 시에만 채워짐. */
    private val _samsungRecords = MutableStateFlow<List<GlucoseRecord>>(emptyList())

    /**
     * 전체 기록 = 사용자 직접 입력(Mock) + 외부 헬스 소스(Samsung).
     * ID 가 다르므로 단순 concat 후 시간 역순 정렬. 동일 시각 중복은 SDK ID 가 보장.
     */
    val records: StateFlow<List<GlucoseRecord>> = combine(
        MockDataProvider.recordsFlow, _samsungRecords
    ) { mock, samsung ->
        (mock + samsung).distinctBy { it.id }.sortedByDescending { it.measuredAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 차트 기간 필터: 7 / 30 / 90일 */
    private val _filterDays = MutableStateFlow(7)
    val filterDays: StateFlow<Int> = _filterDays.asStateFlow()

    /** 필터 적용 기록 (오래된 순 — 차트 X축 용) */
    val filteredForChart: StateFlow<List<GlucoseRecord>> = combine(
        records, _filterDays
    ) { recs, days ->
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        recs.filter { it.measuredAt >= cutoff }.sortedBy { it.measuredAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 이번 주 평균/최고/최저 */
    val weeklyStats: StateFlow<WeeklyStats> = records
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

    init { refresh() }

    fun setFilter(days: Int) { _filterDays.value = days }

    /**
     * 외부 헬스 소스(Samsung Health)에서 최근 90일 혈당을 재조회.
     * 비활성 소스면 빈 리스트 반환 → Mock 기록만 표시됨.
     */
    fun refresh() {
        viewModelScope.launch {
            val fetched = HealthRepository.getBloodGlucoseRecords(days = 90)
            _samsungRecords.value = fetched
            pushGlucoseToServer(fetched)
        }
    }

    /**
     * 외부 헬스 소스에서 가져온 혈당 기록을 백엔드 DB 로 push.
     * 실패해도 UI 흐름은 유지되도록 runCatching 으로 감싼다.
     */
    private suspend fun pushGlucoseToServer(records: List<GlucoseRecord>) {
        if (records.isEmpty()) return
        runCatching { HealthSyncApiClient.pushGlucose(records) }
            .onFailure { Log.w("GlucoseViewModel", "pushGlucoseToServer failed: ${it.message}") }
    }
}
