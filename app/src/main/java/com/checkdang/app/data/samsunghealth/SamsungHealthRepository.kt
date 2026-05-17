package com.checkdang.app.data.samsunghealth

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.error.AuthorizationException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Samsung Health Data SDK v1.1.0 연동 Repository.
 *
 * 개발자 모드 가정 — Partner Apps Program 승인 / SHA-256 등록 없이 동작.
 * 단말 조건: Galaxy + Samsung Health 앱 설치 + One UI 5.1+ (API 29+).
 *
 * 동시 사용 방지: 본 Repository 는 [HealthRepository][com.checkdang.app.data.health.HealthRepository]
 * 가 활성 소스로 선택했을 때만 실제 호출된다. 비활성 상태에서는 어떤 SDK 호출도 발생하지 않으므로
 * Health Connect 경로와 충돌하지 않는다.
 */
class SamsungHealthRepository(private val appContext: Context) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Initializing)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var store: HealthDataStore? = null

    // ── 가용성 / 연결 ────────────────────────────────────────────────────────

    /**
     * SDK 초기화 + 권한 상태 점검. UI 진입 시점에 호출.
     * 실제 SDK 호출은 Build.VERSION.SDK_INT >= 29 에서만 시도한다.
     */
    suspend fun connect(): ConnectionState {
        Log.i(TAG, "connect: start (SDK=${Build.VERSION.SDK_INT}, mfr=${Build.MANUFACTURER})")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.i(TAG, "connect: API<29 → Unsupported")
            return setState(ConnectionState.Unsupported)
        }
        if (!isSamsungDevice()) {
            Log.i(TAG, "connect: non-Samsung device → Unsupported")
            return setState(ConnectionState.Unsupported)
        }
        if (!isSamsungHealthInstalled()) {
            Log.i(TAG, "connect: Samsung Health not installed → NotInstalled")
            return setState(ConnectionState.NotInstalled)
        }

        return runCatching {
            val s = store ?: HealthDataService.getStore(appContext).also { store = it }
            val granted = s.getGrantedPermissions(sdkPermissions())
            Log.i(TAG, "connect: granted=${granted.size}/${sdkPermissions().size} perms")
            if (granted.containsAll(sdkPermissions())) {
                Log.i(TAG, "connect: all granted → Connected")
                setState(ConnectionState.Connected)
            } else {
                Log.i(TAG, "connect: partial → PermissionNeeded")
                setState(ConnectionState.PermissionNeeded)
            }
        }.getOrElse {
            Log.e(TAG, "connect failed", it)
            setState(ConnectionState.Error(it.message ?: "SDK init failed"))
        }
    }

    /**
     * 사용자 로그아웃 / Activity destroy 시 호출. SDK v1.x 는 명시적 disconnect API 가 없어
     * 참조만 끊는다 (호스트와의 IPC 는 GC + lifecycle 로 정리됨).
     */
    fun disconnect() {
        store = null
        _state.value = ConnectionState.Initializing
    }

    private fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private fun isSamsungHealthInstalled(): Boolean = runCatching {
        val pm = appContext.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(SAMSUNG_HEALTH_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(SAMSUNG_HEALTH_PACKAGE, 0)
        }
        true
    }.getOrElse { false }

    // ── 권한 ─────────────────────────────────────────────────────────────────

    /**
     * Samsung Health 앱이 호스팅하는 권한 다이얼로그를 노출. 사용자 선택 결과를 카테고리별로 매핑.
     */
    suspend fun requestPermissions(
        activity: Activity,
        perms: List<HealthDataPermission> = HealthDataPermission.ALL
    ): Map<HealthDataPermission, Boolean> {
        val s = store ?: run {
            Log.w(TAG, "requestPermissions: store is null — connect() 먼저 호출 필요")
            return perms.associateWith { false }
        }
        return runCatching {
            val granted: Set<Permission> = s.requestPermissions(perms.toSdkSet(), activity)
            val result = mapGranted(perms, granted)
            Log.i(TAG, "requestPermissions: result=${result.entries.joinToString { "${it.key.name}=${it.value}" }}")
            if (result.values.any { it }) _state.value = ConnectionState.Connected
            result
        }.getOrElse {
            Log.e(TAG, "requestPermissions failed", it)
            if (it is AuthorizationException) _state.value = ConnectionState.PermissionNeeded
            perms.associateWith { false }
        }
    }

    /** 현재 권한 상태 조회 (UI 새로고침용). */
    suspend fun checkPermissions(
        perms: List<HealthDataPermission> = HealthDataPermission.ALL
    ): Map<HealthDataPermission, Boolean> {
        val s = store ?: return perms.associateWith { false }
        return runCatching {
            mapGranted(perms, s.getGrantedPermissions(perms.toSdkSet()))
        }.getOrElse {
            Log.e(TAG, "checkPermissions failed", it)
            perms.associateWith { false }
        }
    }

    // ── 데이터 읽기 ─────────────────────────────────────────────────────────

    /**
     * 일별 총 걸음 수. SDK 의 `DataTypes.STEPS.TOTAL` aggregate operation 을 사용.
     * 권한 없음 / 데이터 없음 / 오류 시 null.
     */
    suspend fun readSteps(date: LocalDate): Int? {
        val s = store ?: run { Log.w(TAG, "readSteps: store null"); return null }
        return runCatching {
            val filter  = LocalDateFilter.of(date, date.plusDays(1)).toLocalTimeFilter()
            // TOTAL 은 DataType.StepsType 의 static 필드 — 인스턴스(DataTypes.STEPS) 경유 불가
            val request = DataType.StepsType.TOTAL
                .requestBuilder
                .setLocalTimeFilter(filter)
                .build()
            val response = s.aggregateData(request)
            Log.i(TAG, "readSteps[$date]: ${response.dataList.size} aggregated bins")
            val steps = response.dataList.firstOrNull()?.value?.toInt()
            Log.i(TAG, "readSteps[$date]: result=$steps")
            steps
        }.onFailure { Log.e(TAG, "readSteps failed", it) }.getOrNull()
    }

    /** 일별 운동 요약 — DataTypes.EXERCISE read → SESSIONS Field 매핑. */
    suspend fun readExercise(date: LocalDate): ExerciseSummary? {
        val s = store ?: run { Log.w(TAG, "readExercise: store null"); return null }
        return runCatching {
            val request = DataTypes.EXERCISE.readDataRequestBuilder
                .setLocalTimeFilter(date.toFullDayLocalTimeFilter())
                .setOrdering(Ordering.DESC)
                .build()
            val response = s.readData(request)
            Log.i(TAG, "readExercise[$date]: ${response.dataList.size} dataPoints")
            val result = SamsungHealthMapper.toExerciseSummary(response.dataList)
            Log.i(TAG, "readExercise[$date]: mapped → ${result?.let { "${it.totalMinutes}min/${it.sessions.size}sessions" } ?: "null"}")
            result
        }.onFailure { Log.e(TAG, "readExercise failed", it) }.getOrNull()
    }

    /** 일별 식사 요약 — DataTypes.NUTRITION read → 매핑. */
    suspend fun readMeal(date: LocalDate): MealSummary? {
        val s = store ?: run { Log.w(TAG, "readMeal: store null"); return null }
        return runCatching {
            val request = DataTypes.NUTRITION.readDataRequestBuilder
                .setLocalTimeFilter(date.toFullDayLocalTimeFilter())
                .setOrdering(Ordering.ASC)
                .build()
            val response = s.readData(request)
            Log.i(TAG, "readMeal[$date]: ${response.dataList.size} dataPoints")
            val result = SamsungHealthMapper.toMealSummary(response.dataList)
            Log.i(TAG, "readMeal[$date]: mapped → ${result?.let { "${it.totalKcal}kcal/${it.meals.size}meals" } ?: "null"}")
            result
        }.onFailure { Log.e(TAG, "readMeal failed", it) }.getOrNull()
    }

    /** 어젯밤 18:00 ~ 오늘 12:00 수면 요약 — DataTypes.SLEEP read → 매핑. */
    suspend fun readSleep(date: LocalDate): SleepSummary? {
        val s = store ?: run { Log.w(TAG, "readSleep: store null"); return null }
        return runCatching {
            val filter = LocalTimeFilter.of(
                date.minusDays(1).atTime(18, 0),
                date.atTime(12, 0)
            )
            val request = DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(filter)
                .setOrdering(Ordering.DESC)
                .build()
            val response = s.readData(request)
            Log.i(TAG, "readSleep[$date]: ${response.dataList.size} dataPoints")
            val result = SamsungHealthMapper.toSleepSummary(response.dataList)
            Log.i(TAG, "readSleep[$date]: mapped → ${result?.let { "${it.totalHours}h" } ?: "null"}")
            result
        }.onFailure { Log.e(TAG, "readSleep failed", it) }.getOrNull()
    }

    /**
     * 일별 혈당 기록 — DataTypes.BLOOD_GLUCOSE read.
     * 본 앱 핵심 도메인. 각 HealthDataPoint 의 SERIES_DATA / GLUCOSE_LEVEL 을
     * [SamsungHealthMapper.toGlucoseRecords] 가 [GlucoseRecord] 리스트로 평탄화.
     */
    suspend fun readBloodGlucose(date: LocalDate): List<GlucoseRecord> =
        readBloodGlucoseRange(date, date.plusDays(1))

    /**
     * 범위 혈당 기록 — [start] (포함) 부터 [endExclusive] (제외) 까지.
     * 한 번의 SDK 호출로 다중 일자 데이터를 가져온다. 차트의 90일 필터용.
     */
    suspend fun readBloodGlucoseRange(
        start: LocalDate,
        endExclusive: LocalDate
    ): List<GlucoseRecord> {
        val s = store ?: run { Log.w(TAG, "readBloodGlucoseRange: store null"); return emptyList() }
        return runCatching {
            val filter = LocalTimeFilter.of(start.atStartOfDay(), endExclusive.atStartOfDay())
            val request = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
                .setLocalTimeFilter(filter)
                .setOrdering(Ordering.DESC)
                .build()
            val response = s.readData(request)
            Log.i(TAG, "readBloodGlucoseRange[$start~$endExclusive]: ${response.dataList.size} dataPoints")
            val records = SamsungHealthMapper.toGlucoseRecords(response.dataList)
            Log.i(TAG, "readBloodGlucoseRange: mapped → ${records.size} records")
            records
        }.onFailure { Log.e(TAG, "readBloodGlucoseRange failed", it) }.getOrDefault(emptyList())
    }

    /**
     * 최신 체중 (kg) — DataTypes.BODY_COMPOSITION read.
     * 지정 일 ± 30일 범위에서 가장 최근 측정값 반환.
     */
    suspend fun readWeight(date: LocalDate): Float? {
        val s = store ?: return null
        return runCatching {
            val filter = LocalTimeFilter.of(
                date.minusDays(30).atStartOfDay(),
                date.plusDays(1).atStartOfDay()
            )
            val request = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
                .setLocalTimeFilter(filter)
                .setOrdering(Ordering.DESC)
                .build()
            val response = s.readData(request)
            SamsungHealthMapper.toLatestWeight(response.dataList)
        }.onFailure { Log.e(TAG, "readWeight failed", it) }.getOrNull()
    }

    private fun LocalDate.toFullDayLocalTimeFilter(): LocalTimeFilter =
        LocalTimeFilter.of(atStartOfDay(), plusDays(1).atStartOfDay())

    // ── 내부 유틸 ────────────────────────────────────────────────────────────

    private fun setState(s: ConnectionState): ConnectionState {
        _state.value = s
        return s
    }

    private fun List<HealthDataPermission>.toSdkSet(): Set<Permission> =
        mapNotNull { it.toSdkDataType()?.let { dt -> Permission.of(dt, AccessType.READ) } }.toSet()

    private fun mapGranted(
        perms: List<HealthDataPermission>,
        granted: Set<Permission>
    ): Map<HealthDataPermission, Boolean> {
        val grantedDataTypes = granted.map { it.dataType }.toSet()
        return perms.associateWith { perm ->
            perm.toSdkDataType()?.let { it in grantedDataTypes } ?: false
        }
    }

    private fun sdkPermissions(): Set<Permission> =
        HealthDataPermission.ALL.toSdkSet()

    /** [HealthDataPermission] enum → SDK [DataType] 매핑. */
    private fun HealthDataPermission.toSdkDataType(): DataType? = when (this) {
        HealthDataPermission.STEPS         -> DataTypes.STEPS
        HealthDataPermission.EXERCISE      -> DataTypes.EXERCISE
        HealthDataPermission.NUTRITION     -> DataTypes.NUTRITION
        HealthDataPermission.SLEEP         -> DataTypes.SLEEP
        HealthDataPermission.WEIGHT        -> DataTypes.BODY_COMPOSITION
        HealthDataPermission.BLOOD_GLUCOSE -> DataTypes.BLOOD_GLUCOSE
    }

    companion object {
        const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
        private const val TAG = "SamsungHealthRepo"
    }
}

/**
 * SDK 가용성 / 권한 상태 표현.
 * UI 분기는 LifestyleFragment 에서 4가지(Connected/PermissionNeeded/NotInstalled/Unsupported) 처리.
 */
sealed class ConnectionState {
    object Initializing : ConnectionState()
    object Connected : ConnectionState()
    object PermissionNeeded : ConnectionState()
    object NotInstalled : ConnectionState()
    object Unsupported : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
