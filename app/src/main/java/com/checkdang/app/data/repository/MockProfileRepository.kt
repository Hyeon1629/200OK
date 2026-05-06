package com.checkdang.app.data.repository

import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.PatientProfile
import com.checkdang.app.data.remote.ProfileApiClient
import kotlinx.coroutines.delay

class MockProfileRepository {

    private val token get() = SessionHolder.accessToken
    private val hasRealToken get() = !token.isNullOrEmpty() && token != "mock_access_token"

    suspend fun getProfile(): PatientProfile {
        if (hasRealToken) {
            val remote = ProfileApiClient.fetchProfile(token!!)
            SessionHolder.currentProfile = remote
            return remote
        }
        delay(300)
        return SessionHolder.currentProfile ?: SessionHolder.dummyProfile
    }

    suspend fun updateProfile(profile: PatientProfile): PatientProfile {
        if (hasRealToken) {
            val remote = ProfileApiClient.saveProfile(token!!, profile)
            SessionHolder.currentProfile = remote
            return remote
        }
        delay(500)
        SessionHolder.currentProfile = profile
        return profile
    }
}
