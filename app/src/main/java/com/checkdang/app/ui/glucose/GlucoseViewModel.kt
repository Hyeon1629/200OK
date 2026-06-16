package com.checkdang.app.ui.glucose

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.health.HealthRepository
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.InsulinRecord
import com.checkdang.app.data.remote.BloodGlucosePrediction
import com.checkdang.app.data.remote.BloodGlucosePredictionApiClient
import com.checkdang.app.data.remote.GlucoseSyncStore
import com.checkdang.app.data.remote.HealthSyncApiClient
import com.checkdang.app.data.remote.PredictionApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class WeeklyStats(val average: Int, val max: Int, val min: Int)

/** "기록" 타임라인 항목 — 혈당과 인슐린을 시간순으로 함께 표시하기 위한 합집합 타입. */
sealed class TimelineEntry(val time: Long) {
    data class Glucose(val record: GlucoseRecord) : TimelineEntry(record.measuredAt)
    data class Insulin(val record: InsulinRecord) : TimelineEntry(record.injectedAt)
}

/** ML 혈당 예측 화면 상태. */
sealed interface PredictionUiState {
    /** 아직 조회 전(초기). */
    object Idle : PredictionUiState
    object Loading : PredictionUiState
    /** 예측 결과 표시. */
    data class Loaded(val prediction: BloodGlucosePrediction) : PredictionUiState
    /** 아직 예측 전. on-demand 전용이라 진입 시 기본 상태 — "예측하기" 유도. */
    object Empty : PredictionUiState
    data class Error(val message: String) : PredictionUiState
}

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

    /**
     * "기록" 탭 타임라인 = 혈당(직접 입력 + 삼성헬스) + 인슐린(직접 입력)을 시간 역순으로 병합.
     * 혈당 전용 [records] 는 PDF/차트/통계가 사용하므로 그대로 두고, 표시용으로만 합친다.
     */
    val timeline: StateFlow<List<TimelineEntry>> = combine(
        records, MockDataProvider.insulinRecordsFlow
    ) { glucose, insulin ->
        (glucose.map { TimelineEntry.Glucose(it) } + insulin.map { TimelineEntry.Insulin(it) })
            .sortedByDescending { it.time }
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

    fun setFilter(days: Int) { _filterDays.value = days }

    // ── ML 혈당 예측 (FastAPI) ────────────────────────────────────────────────

    private val _prediction = MutableStateFlow<PredictionUiState>(PredictionUiState.Idle)
    val prediction: StateFlow<PredictionUiState> = _prediction.asStateFlow()

    /**
     * 화면 진입 시 상태. 예측은 on-demand 전용(저장/조회 API 없음 — 2026-06-16 계약)이라
     * 진입 시엔 네트워크 호출 없이 "예측하기" 유도 상태(Empty)만 둔다.
     * 이미 결과를 들고 있으면 재진입 시 그대로 유지한다.
     */
    fun loadLatestPrediction() {
        if (_prediction.value is PredictionUiState.Loaded) return
        _prediction.value = PredictionUiState.Empty
    }

    /**
     * "예측하기": 오늘자 예측 실행. 요청 body 없음 → 백엔드가 최신 혈당 직전 24시간
     * (+서버 보관 carbs/bolus)으로 추론한다. 혈당 측정이 부족하면 422 → 데이터 부족 안내.
     */
    fun runPrediction() {
        if (SessionHolder.userId == null) {
            _prediction.value = PredictionUiState.Error("예측은 로그인 후 이용할 수 있어요.")
            return
        }
        viewModelScope.launch {
            _prediction.value = PredictionUiState.Loading
            _prediction.value = runCatching { BloodGlucosePredictionApiClient.predict(today()) }
                .map { PredictionUiState.Loaded(it) }
                .getOrElse { PredictionUiState.Error(messageFor(it)) }
        }
    }

    private fun today(): String = LocalDate.now(ZoneId.of("Asia/Seoul")).toString()

    private fun messageFor(t: Throwable): String = when {
        t is PredictionApiException && t.code == 422 ->
            "예측에 필요한 혈당 데이터가 부족해요. (최소 6회 측정 · 24시간 중 12시간 이상 권장)"
        t is PredictionApiException && t.code == 401 ->
            "로그인이 만료됐어요. 다시 로그인한 뒤 시도해주세요."
        t is PredictionApiException -> t.detail.ifBlank { "예측에 실패했어요." }
        else -> "예측에 실패했어요. 네트워크 상태를 확인해주세요."
    }

    /**
     * 외부 헬스 소스(Samsung Health)에서 최근 90일 혈당을 재조회.
     * 비활성 소스면 빈 리스트 반환 → Mock 기록만 표시됨.
     * 이미 전송한 record 는 [GlucoseSyncStore] 로 걸러 중복 전송을 막는다.
     */
    fun refresh() {
        viewModelScope.launch {
            val fetched = HealthRepository.getBloodGlucoseRecords(days = 90)
            _samsungRecords.value = fetched
            pushGlucoseToServer(GlucoseSyncStore.filterUnsent(fetched))
        }
    }

    /**
     * 사용자가 바텀시트로 직접 입력한 혈당 1건을 백엔드 DB 로 push.
     * 삼성헬스 자동 측정(refresh)과 별개로, 입력 즉시 단건 전송한다.
     * 게스트(userId == null)는 HealthSyncApiClient.pushGlucose 가 스킵한다.
     */
    fun pushManualRecord(record: GlucoseRecord) {
        viewModelScope.launch { pushGlucoseToServer(listOf(record)) }
    }

    /**
     * 사용자가 입력한 인슐린 1건을 백엔드로 push(혈당 예측 bolus 피처용).
     * 게스트(userId == null)는 HealthSyncApiClient.pushInsulin 이 스킵한다.
     * 실패해도 로컬 기록/UI 는 그대로 유지되도록 runCatching 으로 감싼다.
     */
    fun pushInsulinRecord(record: InsulinRecord) {
        viewModelScope.launch {
            runCatching { HealthSyncApiClient.pushInsulin(record) }
                .onFailure { Log.w("GlucoseViewModel", "pushInsulin failed: ${it.message}") }
        }
    }

    /**
     * 혈당 기록을 백엔드 DB 로 push 하고, 전송에 성공한 record 를
     * [GlucoseSyncStore] 에 기록해 이후 재전송을 막는다.
     * 실패해도 UI 흐름은 유지되도록 runCatching 으로 감싼다.
     */
    private suspend fun pushGlucoseToServer(records: List<GlucoseRecord>) {
        if (records.isEmpty()) return
        val sent = runCatching { HealthSyncApiClient.pushGlucose(records) }
            .onFailure { Log.w("GlucoseViewModel", "pushGlucoseToServer failed: ${it.message}") }
            .getOrDefault(emptyList())
        GlucoseSyncStore.markPushed(sent)
    }
}
