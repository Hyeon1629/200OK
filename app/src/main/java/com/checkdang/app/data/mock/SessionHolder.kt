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

    var socialEmail: String? = null
    var socialNickname: String? = null

    // Cognito 발급 ID Token (2026-05-24 백엔드 단일화 후) — Authorization: Bearer {accessToken}
    // 백엔드는 자체 JWT 를 발급하지 않으므로 refreshToken 은 사실상 미사용(호환 위해 잔존).
    var accessToken: String? = null
    var refreshToken: String? = null
    var userId: String? = null

    // 게스트(unauthenticated) 사용자의 Cognito Identity Pool ID.
    // 보호 API 호출 시 `X-Guest-Identity-Id` 헤더로 백엔드 `GuestIdentityFilter` 가 검증.
    var guestIdentityId: String? = null

    // 카카오페이 ready → approve 간 orderId 임시 보관 (메모리만 사용)
    var kakaoPendingOrderId: String? = null

    fun toggleTierForDemo() {
        tier = if (tier == UserTier.PAID) UserTier.FREE else UserTier.PAID
    }

    fun reset() {
        isLoggedIn = false
        isGuest = false
        currentProfile = null
        tier = UserTier.FREE
        authProvider   = SocialProvider.NONE
        socialEmail    = null
        socialNickname = null
        accessToken    = null
        refreshToken   = null
        userId         = null
        guestIdentityId = null
        kakaoPendingOrderId = null
    }

    val dummyProfile = PatientProfile(
        nickname  = "건강이",
        birthDate = "1990-05-15",
        gender    = Gender.MALE,
        heightCm  = 175f,
        weightKg  = 70f
    )
}
