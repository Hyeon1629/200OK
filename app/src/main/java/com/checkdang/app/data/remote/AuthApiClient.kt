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

    // TODO: 실서버 배포 후 URL 변경
    private const val BASE_URL = "https://your-backend-url.com"

    suspend fun socialLogin(provider: String, token: String): SocialLoginResult =
        withContext(Dispatchers.IO) {
            val connection = (URL("$BASE_URL/api/auth/social").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10_000
                readTimeout    = 10_000
                doOutput       = true
            }
            try {
                val body = JSONObject().apply {
                    put("provider", provider)
                    if (provider == "GOOGLE") put("idToken", token)
                    else                      put("accessToken", token)
                }.toString()

                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val text = if (code in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    val err = connection.errorStream?.bufferedReader()?.readText() ?: ""
                    throw Exception("서버 오류 ($code): $err")
                }

                val data = JSONObject(text).getJSONObject("data")
                val user = data.getJSONObject("user")

                SocialLoginResult(
                    accessToken  = data.getString("accessToken"),
                    refreshToken = data.getString("refreshToken"),
                    isNewUser    = user.getBoolean("isNewUser"),
                    userId       = user.getString("id"),
                    email        = user.optString("email").ifEmpty { null },
                    name         = user.optString("name").ifEmpty { null }
                )
            } finally {
                connection.disconnect()
            }
        }
}
