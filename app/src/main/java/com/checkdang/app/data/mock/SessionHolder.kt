package com.checkdang.app.data.mock

import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserTier { GUEST, FREE, PAID }

object SessionHolder {
    var isLoggedIn: Boolean = false
    var isGuest: Boolean = false
    var currentProfile: PatientProfile? = null

    private val _tier = MutableStateFlow(UserTier.FREE)
    val tierFlow: StateFlow<UserTier> = _tier.asStateFlow()

    var tier: UserTier
        get() = _tier.value
        set(value) { _tier.value = value }

    fun toggleTierForDemo() {
        tier = if (tier == UserTier.PAID) UserTier.FREE else UserTier.PAID
    }

    fun reset() {
        isLoggedIn = false
        isGuest = false
        currentProfile = null
        tier = UserTier.FREE
    }

    /** 기존 사용자 시뮬레이션용 더미 프로필 */
    val dummyProfile = PatientProfile(
        nickname  = "건강이",
        birthDate = "1990-05-15",
        gender    = Gender.MALE,
        heightCm  = 175f,
        weightKg  = 70f
    )
}
