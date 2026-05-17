package com.checkdang.app.ui.lifestyle

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.CheckDangApplication
import com.checkdang.app.data.health.HealthConnectDataSource
import com.checkdang.app.data.health.HealthRepository
import com.checkdang.app.data.health.SamsungHealthDataSource
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
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
        _isSyncing.value = false
    }

    companion object {
        private const val TAG = "SamsungHealthRepo"
    }
}
