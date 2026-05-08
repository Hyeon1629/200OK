package com.checkdang.app.ui.lifestyle

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.health.HealthConnectDataSource
import com.checkdang.app.data.health.HealthRepository
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
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
}
