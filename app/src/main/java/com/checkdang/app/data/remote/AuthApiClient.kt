package com.checkdang.app.data.remote

import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AWS Cognito User Pool 단일화(2026-05-24) 이후의 백엔드 인증 클라이언트.
 *
 * 변경 전: `/api/auth/social` 에 provider/idToken/accessToken body 송신 → 자체 JWT 수신.
 * 변경 후: `/api/auth/social-login` 호출 시 body 없음, Authorization 헤더에 **Cognito ID Token** 만.
 *         백엔드는 토큰을 검증해 사용자 row 를 upsert 하고 프로필을 돌려준다(자체 JWT 발급 X).
 *
 * 로그아웃 엔드포인트는 제거(Cognito refresh 토큰은 Amplify 가 관리).
 */
data class SocialLoginResult(
    val userId: String,
    val email: String?,
    val name: String?,
    val role: String?,
    val isPremium: Boolean
)

object AuthApiClient {

    private const val BASE_URL = "https://api.checkdang.xyz"

    /**
     * Cognito ID Token 으로 백엔드에 사용자 row 등록/갱신.
     * 토큰은 [SessionHolder.accessToken] 에 미리 저장되어 있어야 한다 (LoginActivity 에서 fetchAuthSession 후 세팅).
     *
     * 응답 스키마: `{ success, data: { id, email, name, role, isPremium, ... }, message }`
     */
    suspend fun socialLogin(): SocialLoginResult = withContext(Dispatchers.IO) {
        val text = post("/api/auth/social-login")
        val data = JSONObject(text).getJSONObject("data")
        SocialLoginResult(
            userId    = data.getString("id"),
            email     = data.optString("email").ifEmpty { null },
            name      = data.optString("name").ifEmpty { null },
            role      = data.optString("role").ifEmpty { null },
            isPremium = data.optBoolean("isPremium", false)
        )
    }

    /** body 없는 POST. Authorization: Bearer <Cognito ID Token> 만 헤더로 전송. */
    private fun post(path: String): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15_000
            readTimeout    = 15_000
            doOutput       = true
        }
        try {
            conn.outputStream.use { it.write(ByteArray(0)) }
            val code = conn.responseCode
            return if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                val msg = runCatching { JSONObject(err).optString("message", "") }
                    .getOrDefault("").ifEmpty { "서버 오류 ($code)" }
                throw Exception(msg)
            }
        } finally {
            conn.disconnect()
        }
    }
}
