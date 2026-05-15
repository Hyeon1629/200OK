package com.checkdang.app.data.samsunghealth

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Samsung Health Data SDK 연동 Repository (Phase 1 — 스켈레톤).
 *
 * 본 클래스는 Partner Apps Program 승인 + AAR 수령 전 단계에서는
 * **모든 SDK 호출이 비활성화**되어 있다. 가용성 검사(checkAvailability) 는 동작하며,
 * read 메서드는 모두 null 을 반환하여 UI 에 EmptyState 가 표시되도록 한다.
 *
 * Phase 2 활성화 절차:
 *  1. `app/libs/samsung-health-data-api-<version>.aar` 배치
 *  2. `app/build.gradle.kts` 의 AAR fileTree + gson + kotlin-parcelize 활성화
 *  3. 본 파일의 `TODO(samsung-sdk)` 마커 부분을 실제 HealthDataStore 호출로 교체
 *
 * @see docs/STEP11_samsung_health.md §4 아키텍처
 */
class SamsungHealthRepository(private val appContext: Context) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Initializing)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // TODO(samsung-sdk): HealthDataStore 인스턴스 보유
    // private val store: HealthDataStore = HealthDataStore.getStore(appContext)

    // ── 가용성 / 연결 ────────────────────────────────────────────────────────

    /**
     * 단말이 Samsung Health Data SDK 를 지원하는지 + 호스트 앱 설치 여부 확인.
     * Phase 1: 단말 제조사 + 패키지 설치 검사만 수행.
     * Phase 2: SDK 초기화 + 권한 상태까지 검사 후 Connected 진입.
     */
    fun checkAvailability(): ConnectionState {
        val state = when {
            !isSamsungDevice() -> ConnectionState.Unsupported
            !isSamsungHealthInstalled() -> ConnectionState.NotInstalled
            // TODO(samsung-sdk): SDK 초기화 + 권한 상태에 따라 PermissionNeeded / Connected 분기
            else -> ConnectionState.PermissionNeeded
        }
        _state.value = state
        return state
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
     * 5종(또는 일부) 카테고리에 대한 권한 다이얼로그를 호스트(Samsung Health 앱)에 요청.
     * Phase 1: no-op, 모두 false 반환.
     * Phase 2: HealthDataStore.requestPermissions(activity, permsSet) 호출 후 결과 매핑.
     */
    suspend fun requestPermissions(
        @Suppress("UNUSED_PARAMETER") activity: Activity,
        perms: List<HealthDataPermission> = HealthDataPermission.ALL
    ): Map<HealthDataPermission, Boolean> {
        // TODO(samsung-sdk): store.requestPermissions(activity, perms.map { it.toSdkPermission() }.toSet())
        return perms.associateWith { false }
    }

    /**
     * 현재 시점의 권한 허가 상태 조회.
     * Phase 1: 모두 false.
     * Phase 2: HealthDataStore.getGrantedPermissions() 결과 매핑.
     */
    suspend fun checkPermissions(
        perms: List<HealthDataPermission> = HealthDataPermission.ALL
    ): Map<HealthDataPermission, Boolean> {
        // TODO(samsung-sdk): val granted = store.getGrantedPermissions(perms.map { it.toSdkPermission() }.toSet())
        return perms.associateWith { false }
    }

    // ── 데이터 읽기 (모두 try/catch + null 반환) ─────────────────────────────

    /** 일별 총 걸음 수. 권한 없음 / 데이터 없음 / SDK 오류 시 null. */
    suspend fun readSteps(@Suppress("UNUSED_PARAMETER") date: LocalDate): Int? = runCatching {
        // TODO(samsung-sdk): store.aggregate(StepsRequest(date)) → totalSteps
        null
    }.getOrNull()

    /** 일별 운동 요약. null 시 UI EmptyState. */
    suspend fun readExercise(@Suppress("UNUSED_PARAMETER") date: LocalDate): ExerciseSummary? = runCatching {
        // TODO(samsung-sdk): val records = store.readData(ExerciseRequest(date))
        // SamsungHealthMapper.toExerciseSummary(records)
        null
    }.getOrNull()

    /** 일별 식사 요약. null 시 UI EmptyState. */
    suspend fun readMeal(@Suppress("UNUSED_PARAMETER") date: LocalDate): MealSummary? = runCatching {
        // TODO(samsung-sdk): val records = store.readData(NutritionRequest(date))
        // SamsungHealthMapper.toMealSummary(records)
        null
    }.getOrNull()

    /** 어젯밤 ~ 오늘 수면 요약. null 시 UI EmptyState. */
    suspend fun readSleep(@Suppress("UNUSED_PARAMETER") date: LocalDate): SleepSummary? = runCatching {
        // TODO(samsung-sdk): val records = store.readData(SleepRequest(date))
        // SamsungHealthMapper.toSleepSummary(records)
        null
    }.getOrNull()

    /** 최신 체중 (kg). null 시 UI EmptyState. */
    suspend fun readWeight(@Suppress("UNUSED_PARAMETER") date: LocalDate): Float? = runCatching {
        // TODO(samsung-sdk): store.readData(WeightRequest(date)).lastOrNull()?.kg
        null
    }.getOrNull()

    companion object {
        const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
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
