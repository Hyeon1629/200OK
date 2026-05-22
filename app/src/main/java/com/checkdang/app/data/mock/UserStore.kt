package com.checkdang.app.data.mock

import android.content.Context
import android.content.SharedPreferences
import com.checkdang.app.data.model.DiabetesType
import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile

/**
 * 사용자 식별/프로필의 영속 저장소 (SharedPreferences).
 *
 * 키 네임스페이스는 [SocialProvider] 별로 분리되며, 비회원은 [SocialProvider.NONE]
 * 키 공간을 사용한다. 별도의 "게스트 세션 활성" 플래그로 다음 콜드 스타트 시 Splash 가
 * 로그인 화면을 건너뛸지 결정한다.
 */
object UserStore {

    private const val KEY_GUEST_SESSION_ACTIVE = "guest_session_active"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("user_store", Context.MODE_PRIVATE)
    }

    fun isRegistered(provider: SocialProvider): Boolean =
        prefs.getBoolean("registered_${provider.name}", false)

    fun markRegistered(provider: SocialProvider) =
        prefs.edit().putBoolean("registered_${provider.name}", true).apply()

    fun saveProfile(provider: SocialProvider, profile: PatientProfile) {
        prefs.edit()
            .putString("${provider.name}_nickname",       profile.nickname)
            .putString("${provider.name}_birthDate",      profile.birthDate)
            .putString("${provider.name}_gender",         profile.gender.name)
            .putFloat ("${provider.name}_height",         profile.heightCm)
            .putFloat ("${provider.name}_weight",         profile.weightKg)
            .putString("${provider.name}_diabetesType",   profile.diabetesType.name)
            .putString("${provider.name}_diagnosedAt",    profile.diagnosedAt)
            .putInt   ("${provider.name}_fastingTarget",  profile.fastingTargetMgdl)
            .putInt   ("${provider.name}_postMealTarget", profile.postMealTargetMgdl)
            .apply()
    }

    fun getProfile(provider: SocialProvider): PatientProfile? {
        val nickname = prefs.getString("${provider.name}_nickname", null) ?: return null
        return PatientProfile(
            nickname  = nickname,
            birthDate = prefs.getString("${provider.name}_birthDate", "") ?: "",
            gender    = runCatching {
                Gender.valueOf(prefs.getString("${provider.name}_gender", "NONE") ?: "NONE")
            }.getOrDefault(Gender.NONE),
            heightCm  = prefs.getFloat("${provider.name}_height", 0f),
            weightKg  = prefs.getFloat("${provider.name}_weight", 0f),
            diabetesType = runCatching {
                DiabetesType.valueOf(
                    prefs.getString("${provider.name}_diabetesType", "NONE") ?: "NONE"
                )
            }.getOrDefault(DiabetesType.NONE),
            diagnosedAt        = prefs.getString("${provider.name}_diagnosedAt", "") ?: "",
            fastingTargetMgdl  = prefs.getInt("${provider.name}_fastingTarget", 0),
            postMealTargetMgdl = prefs.getInt("${provider.name}_postMealTarget", 0)
        )
    }

    // ── 게스트 세션 영속화 ──────────────────────────────────────────────────
    // 콜드 스타트 시 Splash 가 이 플래그를 읽어 LoginActivity 를 건너뛰고 바로 Main 진입.

    fun isGuestSession(): Boolean =
        prefs.getBoolean(KEY_GUEST_SESSION_ACTIVE, false)

    fun markGuestSession() =
        prefs.edit().putBoolean(KEY_GUEST_SESSION_ACTIVE, true).apply()

    fun clearGuestSession() =
        prefs.edit().putBoolean(KEY_GUEST_SESSION_ACTIVE, false).apply()

    /**
     * 특정 provider 네임스페이스의 모든 키 제거 (회원탈퇴/게스트 데이터 삭제용).
     * [markRegistered] 플래그까지 함께 제거된다.
     */
    fun clearAllForProvider(provider: SocialProvider) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("${provider.name}_") }.forEach { editor.remove(it) }
        editor.remove("registered_${provider.name}")
        editor.apply()
    }
}
