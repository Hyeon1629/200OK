package com.checkdang.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SocialLoginResult(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val name: String?
)

object AuthApiClient {

    private const val BASE_URL = "https://two00ok-u15n.onrender.com"

    private fun post(path: String, body: JSONObject): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout    = 15_000
            doOutput       = true
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            return if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                val msg = runCatching { JSONObject(err).optString("message", "") }
                    .getOrDefault("").ifEmpty { "서버 오류 ($code)" }
                throw Exception(msg)
            }
        } finally {
            conn.disconnect()
        }
    }

    // ── 로그아웃 ──────────────────────────────────────────────────────────────

    suspend fun logout(refreshToken: String) = withContext(Dispatchers.IO) {
        post("/api/auth/logout", JSONObject().apply {
            put("refreshToken", refreshToken)
        })
        Unit
    }

    // ── 소셜 로그인 (Google: idToken, Kakao: accessToken) ────────────────────

    suspend fun socialLogin(
        provider: String,
        idToken: String? = null,
        accessToken: String? = null
    ): SocialLoginResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("provider", provider)
            if (idToken != null)     put("idToken",     idToken)
            if (accessToken != null) put("accessToken", accessToken)
        }
        val text = post("/api/auth/social", body)
        val data = JSONObject(text).getJSONObject("data")
        val user = data.getJSONObject("user")
        SocialLoginResult(
            accessToken  = data.getString("accessToken"),
            refreshToken = data.getString("refreshToken"),
            userId       = user.getString("id"),
            email        = user.optString("email").ifEmpty { null },
            name         = user.optString("name").ifEmpty { null }
        )
    }
}
