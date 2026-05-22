package com.checkdang.app.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserStore
import com.checkdang.app.data.model.DiabetesType
import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val _profile = MutableStateFlow(loadInitial())
    val profile: StateFlow<PatientProfile> = _profile.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private fun loadInitial(): PatientProfile {
        val fromStore = UserStore.getProfile(SessionHolder.authProvider)
        return fromStore
            ?: SessionHolder.currentProfile
            ?: PatientProfile()
    }

    fun updateNickname(nickname: String) {
        _profile.update { it.copy(nickname = nickname.trim()) }
    }

    fun updateBirthDate(date: String) {
        _profile.update { it.copy(birthDate = date) }
    }

    fun updateGender(gender: Gender) {
        _profile.update { it.copy(gender = gender) }
    }

    fun updateBody(heightCm: Float, weightKg: Float) {
        _profile.update { it.copy(heightCm = heightCm, weightKg = weightKg) }
    }

    fun updateDiabetesType(type: DiabetesType) {
        _profile.update { it.copy(diabetesType = type) }
    }

    fun updateDiagnosedAt(yyyyMm: String) {
        _profile.update { it.copy(diagnosedAt = yyyyMm) }
    }

    fun updateTargets(fasting: Int, postMeal: Int) {
        _profile.update {
            it.copy(fastingTargetMgdl = fasting, postMealTargetMgdl = postMeal)
        }
    }

    /** 닉네임만 필수. 나머지는 빈 값 허용 (미입력 상태로 저장됨). */
    fun isValid(): Boolean = _profile.value.nickname.isNotBlank()

    fun save() {
        val profile = _profile.value
        SessionHolder.currentProfile = profile
        // 비회원(NONE) 도 디스크에 영속화 — 콜드 스타트 후에도 프로필이 유지되도록.
        UserStore.saveProfile(SessionHolder.authProvider, profile)
        _saved.value = true
    }
}
