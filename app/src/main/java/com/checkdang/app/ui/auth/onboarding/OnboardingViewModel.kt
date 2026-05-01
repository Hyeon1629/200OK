package com.checkdang.app.ui.auth.onboarding

import androidx.lifecycle.ViewModel
import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OnboardingViewModel : ViewModel() {

    private val _profile = MutableStateFlow(PatientProfile())
    val profile: StateFlow<PatientProfile> = _profile.asStateFlow()

    private var isGuest: Boolean = false

    fun setGuestMode(guest: Boolean) {
        isGuest = guest
    }

    fun isGuestMode(): Boolean = isGuest

    fun updateNickname(nickname: String) {
        _profile.update { it.copy(nickname = nickname) }
    }

    fun updateBirthGender(birthDate: String, gender: Gender) {
        _profile.update { it.copy(birthDate = birthDate, gender = gender) }
    }

    fun updateBody(heightCm: Float, weightKg: Float) {
        _profile.update { it.copy(heightCm = heightCm, weightKg = weightKg) }
    }

    fun buildProfile(): PatientProfile = _profile.value
}
