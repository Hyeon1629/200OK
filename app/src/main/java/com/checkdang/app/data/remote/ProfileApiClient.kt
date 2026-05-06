package com.checkdang.app.data.remote

import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ProfileApiClient {

    private const val BASE_URL = "https://two00ok-8r84.onrender.com"

    private fun request(
        path: String,
        method: String,
        accessToken: String? = null,
        body: JSONObject? = null
    ): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout = 15_000
            if (accessToken != null) {
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            doOutput = body != null
        }

        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            val response = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                val message = runCatching { JSONObject(err).optString("message", "") }
                    .getOrDefault("").ifEmpty { "서버 오류 ($code)" }
                throw Exception(message)
            }

            return response
        } finally {
            conn.disconnect()
        }
    }

    private fun get(path: String, accessToken: String): String = request(path, "GET", accessToken = accessToken)

    private fun put(path: String, accessToken: String, body: JSONObject): String =
        request(path, "PUT", accessToken = accessToken, body = body)

    private fun parseProfile(text: String): PatientProfile {
        val json = JSONObject(text)
        val source = json.optJSONObject("data")
            ?.optJSONObject("profile")
            ?: json.optJSONObject("profile")
            ?: json

        return PatientProfile(
            nickname = source.optString("nickname", ""),
            birthDate = source.optString("birthDate", ""),
            gender = Gender.values().firstOrNull { it.name == source.optString("gender", "NONE") }
                ?: Gender.NONE,
            heightCm = source.optDouble("heightCm", source.optDouble("height", 0.0)).toFloat(),
            weightKg = source.optDouble("weightKg", source.optDouble("weight", 0.0)).toFloat()
        )
    }

    suspend fun fetchProfile(accessToken: String): PatientProfile = withContext(Dispatchers.IO) {
        parseProfile(get("/api/user/profile", accessToken))
    }

    suspend fun saveProfile(accessToken: String, profile: PatientProfile): PatientProfile = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("nickname", profile.nickname)
            put("birthDate", profile.birthDate)
            put("gender", profile.gender.name)
            put("heightCm", profile.heightCm)
            put("weightKg", profile.weightKg)
        }
        parseProfile(put("/api/user/profile", accessToken, body))
    }
}
