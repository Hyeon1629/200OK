package com.checkdang.app.ui.lifestyle

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.CheckDangApplication
import com.checkdang.app.data.device.DeviceIdProvider
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.health.HealthConnectDataSource
import com.checkdang.app.data.health.HealthRepository
import com.checkdang.app.data.health.SamsungHealthDataSource
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import com.checkdang.app.data.remote.HealthSyncApiClient
import java.time.LocalDate
import com.checkdang.app.data.samsunghealth.ConnectionState
import com.checkdang.app.data.samsunghealth.SamsungHealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LifestyleViewModel(app: Application) : AndroidViewModel(app) {

    private val _exercise  = MutableStateFlow<ExerciseSummary?>(null)
    val exercise: StateFlow<ExerciseSummary?> = _exercise.asStateFlow()

    private val _meal = MutableStateFlow<MealSummary?>(null)
    val meal: StateFlow<MealSummary?> = _meal.asStateFlow()

    private val _sleep = MutableStateFlow<SleepSummary?>(null)
    val sleep: StateFlow<SleepSummary?> = _sleep.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init { sync() }

    /**
     * HealthRepository 현재 소스(Mock 또는 HealthConnect)로 데이터 재로드.
     * 화면 갱신 버튼 / 권한 허가 직후 호출.
     */
    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _exercise.value  = HealthRepository.getExerciseSummary()
            _meal.value      = HealthRepository.getMealSummary()
            _sleep.value     = HealthRepository.getSleepSummary()
            pushLifestyleToServer(source = currentSourceLabel())
            _isSyncing.value = false
        }
    }

    /**
     * Health Connect 권한 허가 완료 후 호출.
     * HealthRepository를 HealthConnectDataSource로 교체 후 데이터를 한 코루틴 안에서 로드한다.
     */
    fun connectAndSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            runCatching {
                val client = HealthConnectClient.getOrCreate(getApplication())
                HealthRepository.switchToHealthConnect(HealthConnectDataSource(client))
                _exercise.value = HealthRepository.getExerciseSummary()
                _meal.value     = HealthRepository.getMealSummary()
                _sleep.value    = HealthRepository.getSleepSummary()
            }
            pushLifestyleToServer(source = "health_connect")
            _isSyncing.value = false
        }
    }

    /**
     * Samsung Health Data SDK 우선 시도. 결과:
     *  - true: Samsung 소스로 전환 + 데이터 로드 완료
     *  - false: Samsung 사용 불가/거부 — 호출자가 Health Connect fallback 진행 가능
     *
     * [requestPermissionIfNeeded] = true 면 권한 부족 시 Samsung Health 호스트의 권한 다이얼로그를
     * 노출(사용자 명시적 새로고침). false 면 이미 허가된 경우에만 활성화(진입 시 silent 체크).
     */
    suspend fun trySamsungHealthFirst(
        activity: Activity,
        requestPermissionIfNeeded: Boolean
    ): Boolean {
        val repo: SamsungHealthRepository =
            (getApplication() as CheckDangApplication).samsungHealthRepository

        val state = repo.connect()
        Log.i(TAG, "trySamsungHealthFirst: state=$state, requestPerm=$requestPermissionIfNeeded")
        return when (state) {
            ConnectionState.Connected -> {
                activateSamsung(repo)
                true
            }
            ConnectionState.PermissionNeeded -> {
                if (!requestPermissionIfNeeded) {
                    Log.i(TAG, "trySamsungHealthFirst: PermissionNeeded but silent mode — skip")
                    return false
                }
                val granted = repo.requestPermissions(activity)
                val anyGranted = granted.values.any { it }
                Log.i(TAG, "trySamsungHealthFirst: anyGranted=$anyGranted")
                if (anyGranted) {
                    activateSamsung(repo)
                    true
                } else false
            }
            ConnectionState.NotInstalled,
            ConnectionState.Unsupported,
            ConnectionState.Initializing,
            is ConnectionState.Error -> false
        }
    }

    private suspend fun activateSamsung(repo: SamsungHealthRepository) {
        Log.i(TAG, "activateSamsung: switching HealthRepository source → Samsung")
        _isSyncing.value = true
        HealthRepository.switchToSamsungHealth(SamsungHealthDataSource(repo))
        _exercise.value = HealthRepository.getExerciseSummary()
        _meal.value     = HealthRepository.getMealSummary()
        _sleep.value    = HealthRepository.getSleepSummary()
        Log.i(TAG, "activateSamsung: done — ex=${_exercise.value != null}, meal=${_meal.value != null}, sleep=${_sleep.value != null}")
        pushLifestyleToServer(source = "samsung_health")
        _isSyncing.value = false
    }

    /**
     * 동기화 완료 후 백엔드 DB 로 라이프스타일 데이터를 전송한다.
     * - 운동: POST /api/samsung-health/exercises (ExerciseSyncRequest[])
     * - 식사: POST /api/samsung-health/diets    (DietSyncRequest[])
     * - 수면: POST /api/samsung-health/sleeps   (SleepSyncRequest[1])
     * 각 호출 실패는 개별적으로 silent 로깅하여 다른 카테고리 송신을 막지 않는다.
     */
    private suspend fun pushLifestyleToServer(source: String) {
        // 게스트는 백엔드 동기화 대상이 아니다(Spring 게스트는 /api/home 만 허용, FastAPI 는 게스트 미지원).
        if (SessionHolder.isGuest) {
            Log.i(TAG, "pushLifestyleToServer[$source]: guest — skip backend sync")
            return
        }
        // Mock(기본 소스)은 절대 백엔드로 올리지 않는다. 실제 헬스 소스가 연결됐을 때만 push.
        // 진입 직후/권한 전환 전에는 활성 소스가 MockHealthDataSource 라서, 가드가 없으면
        // Mock 식단 픽스처(아몬드+그릭요거트, 샐러드+두부 등)가 DB 에 유출돼 AI 리포트/식단 조언이
        // 실제 섭취와 무관한 내용을 읽게 된다.
        if (!HealthRepository.isConnectedToHealthConnect() &&
            !HealthRepository.isConnectedToSamsungHealth()
        ) {
            Log.i(TAG, "pushLifestyleToServer[$source]: mock source — skip backend sync")
            return
        }
        val ex = _exercise.value
        val ml = _meal.value
        val sl = _sleep.value
        Log.i(TAG, "pushLifestyleToServer[$source]: ex=${ex?.sessions?.size ?: 0}, meals=${ml?.meals?.size ?: 0}, sleep=${sl != null}")

        ex?.sessions?.takeIf { it.isNotEmpty() }?.let { sessions ->
            runCatching { HealthSyncApiClient.pushExercises(sessions) }
                .onFailure { Log.w(TAG, "pushExercises failed: ${it.message}") }
        }
        ml?.meals?.takeIf { it.isNotEmpty() }?.let { meals ->
            runCatching { HealthSyncApiClient.pushDiets(meals) }
                .onFailure { Log.w(TAG, "pushDiets failed: ${it.message}") }
        }
        sl?.let { sleep ->
            runCatching { HealthSyncApiClient.pushSleep(sleep) }
                .onFailure { Log.w(TAG, "pushSleep failed: ${it.message}") }
        }

        // FastAPI: step_calorie / heart_rate — Samsung Health 활성 시에만 데이터 제공
        if (HealthRepository.isConnectedToSamsungHealth()) {
            val today    = LocalDate.now()
            val deviceId = DeviceIdProvider.get(getApplication())

            val steps = HealthRepository.getStepCount(today)
            if (steps != null && steps > 0) {
                val calorie = ex?.totalCalories?.toDouble() ?: 0.0
                runCatching { HealthSyncApiClient.pushStepCalorie(today, steps, calorie, deviceId) }
                    .onFailure { Log.w(TAG, "pushStepCalorie failed: ${it.message}") }
            }

            val heartRates = HealthRepository.getHeartRates(today)
            if (heartRates.isNotEmpty()) {
                runCatching { HealthSyncApiClient.pushHeartRates(today, heartRates, deviceId) }
                    .onFailure { Log.w(TAG, "pushHeartRates failed: ${it.message}") }
            }
        }
    }

    private fun currentSourceLabel(): String = when {
        HealthRepository.isConnectedToSamsungHealth() -> "samsung_health"
        HealthRepository.isConnectedToHealthConnect() -> "health_connect"
        else -> "mock"
    }

    companion object {
        private const val TAG = "SamsungHealthRepo"
    }
}
