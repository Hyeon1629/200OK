package com.checkdang.app.data.mock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.checkdang.app.data.model.AIAnalysisResult
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.Correlation
import com.checkdang.app.data.model.CorrelationLevel
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.FamilyMember
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.GlucoseSummary
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.data.model.PainTaxonomy
import com.checkdang.app.data.model.SleepSummary
import com.checkdang.app.util.MealTiming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 사용자가 직접 입력한 기록(혈당/통증)의 in-memory + SharedPreferences 영속 저장소.
 *
 * Room/DB 도입 제약(CLAUDE.md) 하에서 게스트·로그인 사용자 모두 앱 재시작 후에도
 * 본인이 입력한 기록을 유지하기 위한 최소 구현. 직렬화는 [org.json] 사용 (앱 내 다른
 * 모듈과 동일한 라이브러리).
 */
object MockDataProvider {

    private const val TAG = "MockDataProvider"
    private const val PREFS_NAME = "mock_data_store"
    private const val KEY_GLUCOSE_RECORDS = "glucose_records"
    private const val KEY_PAIN_RECORDS    = "pain_records"

    private var prefs: SharedPreferences? = null

    /**
     * Application.onCreate() 에서 [UserStore.init] 와 함께 호출. 디스크의 기록을 메모리로 로드.
     * 컨텍스트가 주입되기 전까지는 메모리 전용으로 동작 (테스트/프리뷰 보호).
     */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        restoreGlucose()
        restorePain()
    }

    // ── Glucose Records ──────────────────────────────────────────────────────

    private val _records: MutableList<GlucoseRecord> = mutableListOf()

    private val _recordsFlow = MutableStateFlow<List<GlucoseRecord>>(
        _records.sortedByDescending { it.measuredAt }
    )
    val recordsFlow: StateFlow<List<GlucoseRecord>> = _recordsFlow.asStateFlow()

    fun getAllRecords(): List<GlucoseRecord> = _records.sortedByDescending { it.measuredAt }

    fun getWeeklyRecords(): List<GlucoseRecord> {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return getAllRecords().filter { it.measuredAt >= cutoff }
    }

    fun addRecord(record: GlucoseRecord) {
        _records.add(record)
        _recordsFlow.value = _records.sortedByDescending { it.measuredAt }
        persistGlucose()
    }

    private fun restoreGlucose() {
        val raw = prefs?.getString(KEY_GLUCOSE_RECORDS, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<GlucoseRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += GlucoseRecord(
                    id         = o.getString("id"),
                    value      = o.getInt("value"),
                    timing     = MealTiming.valueOf(o.getString("timing")),
                    measuredAt = o.getLong("measuredAt"),
                    memo       = if (o.isNull("memo")) null else o.optString("memo")
                )
            }
            _records.clear()
            _records.addAll(list)
            _recordsFlow.value = _records.sortedByDescending { it.measuredAt }
            Log.i(TAG, "restoreGlucose: loaded ${list.size} records")
        }.onFailure { Log.w(TAG, "restoreGlucose failed: ${it.message}") }
    }

    private fun persistGlucose() {
        val store = prefs ?: return
        val arr = JSONArray()
        _records.forEach { r ->
            arr.put(JSONObject().apply {
                put("id",         r.id)
                put("value",      r.value)
                put("timing",     r.timing.name)
                put("measuredAt", r.measuredAt)
                if (r.memo != null) put("memo", r.memo) else put("memo", JSONObject.NULL)
            })
        }
        store.edit().putString(KEY_GLUCOSE_RECORDS, arr.toString()).apply()
    }

    // ── Summary / Lifestyle ──────────────────────────────────────────────────

    fun getGlucoseSummary(): GlucoseSummary? = null   // TODO(backend): 서버에서 오늘의 요약 로드

    fun getLifestyleSummary(): LifestyleSummary? = null  // LifestyleViewModel → HealthRepository 직접 사용

    fun getWeeklyGlucose(): List<Float> = emptyList()

    // ── Lifestyle — LifestyleViewModel/DetailActivity에서 HealthRepository로 직접 조회 ──

    fun getExerciseSummary(): ExerciseSummary? = null
    fun getMealSummary(): MealSummary?         = null
    fun getSleepSummary(): SleepSummary?       = null

    /** @deprecated HealthRepository.getWeeklyExerciseMinutes() 직접 사용 */
    fun getWeeklyExerciseMinutes(): List<Int>  = emptyList()

    /** @deprecated HealthRepository.getWeeklySleepHours() 직접 사용 */
    fun getWeeklySleepHours(): List<Float>     = emptyList()

    // ── Pain Records ─────────────────────────────────────────────────────────

    private val _painRecords: MutableList<PainRecord> = mutableListOf()

    private val _painRecordsFlow = MutableStateFlow<List<PainRecord>>(
        _painRecords.sortedByDescending { it.recordedAt }
    )
    val painRecordsFlow: StateFlow<List<PainRecord>> = _painRecordsFlow.asStateFlow()

    fun addPainRecord(record: PainRecord) {
        _painRecords.add(record)
        _painRecordsFlow.value = _painRecords.sortedByDescending { it.recordedAt }
        persistPain()
    }

    private fun restorePain() {
        val raw = prefs?.getString(KEY_PAIN_RECORDS, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<PainRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                fun strList(key: String): List<String> {
                    val a = o.optJSONArray(key) ?: return emptyList()
                    return (0 until a.length()).map { idx -> a.getString(idx) }
                }
                list += PainRecord(
                    id            = o.getString("id"),
                    bodyPart      = BodyPart.valueOf(o.getString("bodyPart")),
                    intensity     = o.getInt("intensity"),
                    qualityTags   = strList("qualityTags"),
                    situationTags = strList("situationTags"),
                    recordedAt    = o.getLong("recordedAt")
                )
            }
            _painRecords.clear()
            _painRecords.addAll(list)
            _painRecordsFlow.value = _painRecords.sortedByDescending { it.recordedAt }
            Log.i(TAG, "restorePain: loaded ${list.size} records")
        }.onFailure { Log.w(TAG, "restorePain failed: ${it.message}") }
    }

    private fun persistPain() {
        val store = prefs ?: return
        val arr = JSONArray()
        _painRecords.forEach { p ->
            arr.put(JSONObject().apply {
                put("id",            p.id)
                put("bodyPart",      p.bodyPart.name)
                put("intensity",     p.intensity)
                put("recordedAt",    p.recordedAt)
                put("qualityTags",   JSONArray().apply { p.qualityTags.forEach { put(it) } })
                put("situationTags", JSONArray().apply { p.situationTags.forEach { put(it) } })
            })
        }
        store.edit().putString(KEY_PAIN_RECORDS, arr.toString()).apply()
    }

    /**
     * 게스트 회원탈퇴 등으로 사용자 직접 입력 기록을 일괄 삭제.
     * 가족 구성원은 in-memory 전용이라 별도 삭제 불필요.
     */
    fun clearAllUserData() {
        _records.clear()
        _painRecords.clear()
        _recordsFlow.value = emptyList()
        _painRecordsFlow.value = emptyList()
        prefs?.edit()
            ?.remove(KEY_GLUCOSE_RECORDS)
            ?.remove(KEY_PAIN_RECORDS)
            ?.apply()
    }

    // TODO(backend): 실제 AI 분석 API로 교체 — 현재는 부위/유형 기반 규칙 기반 목 분석
    fun analyzePainMock(record: PainRecord): AIAnalysisResult {
        val partLabel = record.bodyPart.label
        val correlations = buildCorrelations(record)
        return AIAnalysisResult(
            painRecord     = record,
            summary        = "${partLabel} 통증 패턴 분석 결과, 최근 혈당 변동 및 수면 부족과의 연관성이 감지되었습니다. " +
                             "통증 강도 ${record.intensity}/5 수준으로 지속적인 모니터링이 권장됩니다.",
            correlations   = correlations,
            recommendation = "규칙적인 스트레칭과 충분한 수면(7~8시간)을 유지하세요. " +
                             "혈당을 안정적으로 관리하면 신경 관련 통증 완화에 도움이 될 수 있습니다. " +
                             "통증이 지속되거나 악화될 경우 전문의 상담을 권장합니다."
        )
    }

    private fun buildCorrelations(record: PainRecord): List<Correlation> {
        val list = mutableListOf<Correlation>()
        // Glucose correlation — always include
        list += Correlation(
            factor      = "혈당 변동성",
            level       = if (record.intensity >= 4) CorrelationLevel.HIGH else CorrelationLevel.MEDIUM,
            description = "최근 7일간 혈당 변동폭이 크게 나타났습니다. 고혈당 상태는 신경 염증을 악화시킬 수 있습니다."
        )
        // Sleep correlation
        list += Correlation(
            factor      = "수면 부족",
            level       = CorrelationLevel.MEDIUM,
            description = "수면 시간이 권장 기준(7~8시간)보다 낮은 날과 통증 기록이 겹치는 경향이 있습니다."
        )
        // Exercise correlation depending on body part
        if (record.bodyPart in listOf(BodyPart.LOWER_BACK, BodyPart.LEFT_KNEE, BodyPart.RIGHT_KNEE,
                BodyPart.LEFT_THIGH_FRONT, BodyPart.RIGHT_THIGH_FRONT)) {
            list += Correlation(
                factor      = "운동 강도",
                level       = CorrelationLevel.LOW,
                description = "기록된 운동 세션과 해당 부위 통증 사이의 낮은 상관관계가 발견되었습니다."
            )
        }
        // 통증 성질 기반 상관관계 — 신경성 태그가 있으면 신경 민감도 추가
        if (record.qualityTags.any { it in PainTaxonomy.NEURAL_TAGS }) {
            list += Correlation(
                factor      = "신경 민감도",
                level       = CorrelationLevel.HIGH,
                description = "저림·타는 느낌·방사통 등은 신경 관련 증상일 수 있으며, 혈당 조절과 밀접한 연관이 있습니다."
            )
        }
        return list
    }

    // ── Family Members ───────────────────────────────────────────────────────

    private val _familyMembers: MutableList<FamilyMember> = mutableListOf()

    private val _familyFlow = MutableStateFlow<List<FamilyMember>>(
        _familyMembers.toList()
    )
    val familyFlow: StateFlow<List<FamilyMember>> = _familyFlow.asStateFlow()

    fun addFamilyMember(member: FamilyMember) {
        _familyMembers.add(member)
        _familyFlow.value = _familyMembers.toList()
    }

    fun removeFamilyMember(id: String) {
        _familyMembers.removeAll { it.id == id }
        _familyFlow.value = _familyMembers.toList()
    }

    fun getFamilyMembers(): List<FamilyMember> = _familyMembers.toList()
}
