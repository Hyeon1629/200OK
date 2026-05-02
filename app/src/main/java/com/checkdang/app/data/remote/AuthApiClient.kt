package com.checkdang.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SocialLoginResult(
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean,
    val userId: String,
    val email: String?,
    val name: String?
)

object AuthApiClient {

    private const val BASE_URL = "https://two00ok-8r84.onrender.com"

    // ── 공통 POST 헬퍼 ───────────────────────────────────────────────────────

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

    // ── 1단계: 이메일 회원가입 ────────────────────────────────────────────────

    suspend fun signupEmail(
        email: String,
        password: String,
        name: String,
        termsAgreed: Boolean,
        birthDate: String,
        gender: String,
        height: Int,
        weight: Int
    ) = withContext(Dispatchers.IO) {
        post("/api/auth/signup", JSONObject().apply {
            put("email",       email)
            put("password",    password)
            put("name",        name)
            put("role",        "PATIENT")
            put("termsAgreed", termsAgreed)
            put("birthDate",   birthDate)
            put("gender",      gender)
            put("height",      height)
            put("weight",      weight)
        })
        Unit
    }

    // ── 소셜 로그인 (2단계 이전까지 미사용, 추후 연동 예정) ───────────────────

    suspend fun socialLogin(provider: String, token: String): SocialLoginResult =
        withContext(Dispatchers.IO) {
            SocialLoginResult(
                accessToken  = "mock_access_token",
                refreshToken = "mock_refresh_token",
                isNewUser    = false,
                userId       = "mock_user_id",
                email        = null,
                name         = null
            )
        }
}
