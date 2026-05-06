package com.checkdang.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile
import com.checkdang.app.data.repository.MockProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val profile: PatientProfile) : ProfileUiState()
    object SaveSuccess : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel : ViewModel() {

    private val repository = MockProfileRepository()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            runCatching { repository.getProfile() }
                .onSuccess { _uiState.value = ProfileUiState.Success(it) }
                .onFailure { _uiState.value = ProfileUiState.Error(it.message ?: "프로필 불러오기 실패") }
        }
    }

    fun saveProfile(
        nickname: String,
        birthDate: String,
        gender: Gender,
        heightCm: Float,
        weightKg: Float
    ) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val updated = PatientProfile(nickname, birthDate, gender, heightCm, weightKg)
            runCatching { repository.updateProfile(updated) }
                .onSuccess { _uiState.value = ProfileUiState.SaveSuccess }
                .onFailure { _uiState.value = ProfileUiState.Error(it.message ?: "저장 실패") }
        }
    }
}
