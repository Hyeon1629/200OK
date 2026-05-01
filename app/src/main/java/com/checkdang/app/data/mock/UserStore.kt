package com.checkdang.app.data.mock

import android.content.Context
import android.content.SharedPreferences
import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile

object UserStore {

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
            .putString("${provider.name}_nickname",  profile.nickname)
            .putString("${provider.name}_birthDate", profile.birthDate)
            .putString("${provider.name}_gender",    profile.gender.name)
            .putFloat ("${provider.name}_height",    profile.heightCm)
            .putFloat ("${provider.name}_weight",    profile.weightKg)
            .apply()
    }

    fun getProfile(provider: SocialProvider): PatientProfile? {
        val nickname = prefs.getString("${provider.name}_nickname", null) ?: return null
        return PatientProfile(
            nickname  = nickname,
            birthDate = prefs.getString("${provider.name}_birthDate", "") ?: "",
            gender    = Gender.valueOf(prefs.getString("${provider.name}_gender", "NONE") ?: "NONE"),
            heightCm  = prefs.getFloat("${provider.name}_height", 0f),
            weightKg  = prefs.getFloat("${provider.name}_weight", 0f)
        )
    }
}
