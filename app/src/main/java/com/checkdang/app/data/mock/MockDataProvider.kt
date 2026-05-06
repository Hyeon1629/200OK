package com.checkdang.app.data.mock

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
import com.checkdang.app.data.model.PainType
import com.checkdang.app.data.model.SleepSummary
import com.checkdang.app.util.MealTiming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MockDataProvider {

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
    }

    // TODO(backend): 실제 API 연동 시 서버에서 레코드를 로드하여 _records에 주입

    // ── Summary / Lifestyle ──────────────────────────────────────────────────

    fun getGlucoseSummary(): GlucoseSummary? = null   // TODO(backend): 서버에서 오늘의 요약 로드

    fun getLifestyleSummary(): LifestyleSummary? = null // TODO(backend): 서버에서 라이프스타일 요약 로드

    fun getWeeklyGlucose(): List<Float> = emptyList()

    // ── Lifestyle ────────────────────────────────────────────────────────────

    fun getExerciseSummary(): ExerciseSummary? = null   // TODO(backend): 서버에서 운동 요약 로드

    fun getMealSummary(): MealSummary? = null           // TODO(backend): 서버에서 식사 요약 로드

    fun getSleepSummary(): SleepSummary? = null         // TODO(backend): 서버에서 수면 요약 로드

    /** 주간 운동 시간 (분, 오늘=마지막) */
    fun getWeeklyExerciseMinutes(): List<Int> = emptyList()

    /** 주간 수면 시간 (시간, 오늘=마지막) */
    fun getWeeklySleepHours(): List<Float> = emptyList()

    // ── Pain Records ─────────────────────────────────────────────────────────

    private val _painRecords: MutableList<PainRecord> = mutableListOf()

    private val _painRecordsFlow = MutableStateFlow<List<PainRecord>>(
        _painRecords.sortedByDescending { it.recordedAt }
    )
    val painRecordsFlow: StateFlow<List<PainRecord>> = _painRecordsFlow.asStateFlow()

    fun addPainRecord(record: PainRecord) {
        _painRecords.add(record)
        _painRecordsFlow.value = _painRecords.sortedByDescending { it.recordedAt }
    }

    // TODO(backend): 실제 AI 분석 API로 교체 — 현재는 부위/유형 기반 규칙 기반 목 분석
    fun analyzePainMock(record: PainRecord): AIAnalysisResult {
        val partLabel = record.bodyPart.label
        val correlations = buildCorrelations(record)
        return AIAnalysisResult(
            painRecord     = record,
            summary        = "${partLabel} 통증 패턴 분석 결과, 최근 혈당 변동 및 수면 부족과의 연관성이 감지되었습니다. " +
                             "통증 강도 ${record.intensity}/10 수준으로 지속적인 모니터링이 권장됩니다.",
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
        // Pain type-based correlation
        if (PainType.NUMBNESS in record.painTypes || PainType.BURNING in record.painTypes) {
            list += Correlation(
                factor      = "신경 민감도",
                level       = CorrelationLevel.HIGH,
                description = "저림·타는 느낌은 신경 관련 증상일 수 있으며, 혈당 조절과 밀접한 연관이 있습니다."
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
