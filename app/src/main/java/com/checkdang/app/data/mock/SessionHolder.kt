package com.checkdang.app.data.mock

import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserTier { GUEST, FREE, PAID }

enum class SocialProvider(val labelKr: String) {
    GOOGLE("구글"),
    KAKAO("카카오"),
    NONE("비회원")
}

object SessionHolder {
    var isLoggedIn: Boolean = false
    var isGuest: Boolean = false
    var currentProfile: PatientProfile? = null

    private val _tier = MutableStateFlow(UserTier.FREE)
    val tierFlow: StateFlow<UserTier> = _tier.asStateFlow()

    var tier: UserTier
        get() = _tier.value
        set(value) { _tier.value = value }

    var authProvider: SocialProvider = SocialProvider.NONE
    var hasEverLoggedIn: Boolean = false   // 데모용 신규/기존 사용자 분기

    // TODO(backend, persistence): EncryptedSharedPreferences 또는 DataStore 로 토큰 영속화
    //  - accessToken, refreshToken 저장
    //  - 앱 재시작 시 자동 로그인
    var socialEmail: String? = null
    var socialNickname: String? = null

    fun toggleTierForDemo() {
        tier = if (tier == UserTier.PAID) UserTier.FREE else UserTier.PAID
    }

    fun reset() {
        isLoggedIn = false
        isGuest = false
        currentProfile = null
        tier = UserTier.FREE
        authProvider = SocialProvider.NONE
        socialEmail = null
        socialNickname = null
        // hasEverLoggedIn 은 의도적으로 보존 (데모 흐름 유지)
    }

    val dummyProfile = PatientProfile(
        nickname  = "건강이",
        birthDate = "1990-05-15",
        gender    = Gender.MALE,
        heightCm  = 175f,
        weightKg  = 70f
    )
}
