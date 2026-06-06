package com.checkdang.app.data.mock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.FamilyMember
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.GlucoseSummary
import com.checkdang.app.data.model.InsulinRecord
import com.checkdang.app.data.model.InsulinType
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.PainRecord
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
    private const val KEY_INSULIN_RECORDS = "insulin_records"
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
        restoreInsulin()
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

    // ── Insulin Records ──────────────────────────────────────────────────────

    private val _insulinRecords: MutableList<InsulinRecord> = mutableListOf()

    private val _insulinRecordsFlow = MutableStateFlow<List<InsulinRecord>>(
        _insulinRecords.sortedByDescending { it.injectedAt }
    )
    val insulinRecordsFlow: StateFlow<List<InsulinRecord>> = _insulinRecordsFlow.asStateFlow()

    fun addInsulinRecord(record: InsulinRecord) {
        _insulinRecords.add(record)
        _insulinRecordsFlow.value = _insulinRecords.sortedByDescending { it.injectedAt }
        persistInsulin()
    }

    private fun restoreInsulin() {
        val raw = prefs?.getString(KEY_INSULIN_RECORDS, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<InsulinRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += InsulinRecord(
                    id         = o.getString("id"),
                    units      = o.getDouble("units").toFloat(),
                    type       = InsulinType.valueOf(o.getString("type")),
                    injectedAt = o.getLong("injectedAt"),
                    memo       = if (o.isNull("memo")) null else o.optString("memo")
                )
            }
            _insulinRecords.clear()
            _insulinRecords.addAll(list)
            _insulinRecordsFlow.value = _insulinRecords.sortedByDescending { it.injectedAt }
            Log.i(TAG, "restoreInsulin: loaded ${list.size} records")
        }.onFailure { Log.w(TAG, "restoreInsulin failed: ${it.message}") }
    }

    private fun persistInsulin() {
        val store = prefs ?: return
        val arr = JSONArray()
        _insulinRecords.forEach { r ->
            arr.put(JSONObject().apply {
                put("id",         r.id)
                put("units",      r.units.toDouble())
                put("type",       r.type.name)
                put("injectedAt", r.injectedAt)
                if (r.memo != null) put("memo", r.memo) else put("memo", JSONObject.NULL)
            })
        }
        store.edit().putString(KEY_INSULIN_RECORDS, arr.toString()).apply()
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
        _insulinRecords.clear()
        _painRecords.clear()
        _recordsFlow.value = emptyList()
        _insulinRecordsFlow.value = emptyList()
        _painRecordsFlow.value = emptyList()
        prefs?.edit()
            ?.remove(KEY_GLUCOSE_RECORDS)
            ?.remove(KEY_INSULIN_RECORDS)
            ?.remove(KEY_PAIN_RECORDS)
            ?.apply()
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
