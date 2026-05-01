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
    var savedProfile: PatientProfile? = null  // 로그아웃 후 재로그인 시 프로필 복원용

    var socialEmail: String? = null
    var socialNickname: String? = null

    // 백엔드 발급 토큰 — Authorization: Bearer {accessToken} 헤더에 사용
    var accessToken: String? = null
    var refreshToken: String? = null
    var userId: String? = null

    fun toggleTierForDemo() {
        tier = if (tier == UserTier.PAID) UserTier.FREE else UserTier.PAID
    }

    fun reset() {
        isLoggedIn = false
        isGuest = false
        currentProfile = null
        tier = UserTier.FREE
        authProvider = SocialProvider.NONE
        socialEmail    = null
        socialNickname = null
        accessToken    = null
        refreshToken   = null
        userId         = null
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
