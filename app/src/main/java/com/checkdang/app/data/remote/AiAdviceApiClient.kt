package com.checkdang.app.data.remote

import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiAdviceApiClient {
    // `/api/ai/diet-advice/recent` 은 Spring 정식 경로(`/api/*` → ALB 가 Spring 으로 라우팅).
    // 실제 사용자 식단(기본 최근 7일)을 분석한다. (구 경로 `/api/ai/demo-diet-advice` 대체, 2026-06-14 백엔드 회신)
    // 로그인 사용자 전용 — 게스트는 호출하지 않는다(SecurityConfig 상 게스트는 `/api/home/**` 만 허용).
    private const val BASE_URL = "https://api.checkdang.xyz"

    suspend fun getDietAdviceForDemo(): String =
        withContext(Dispatchers.IO) {
            val text = get("/api/ai/diet-advice/recent")
            JSONObject(text).getString("answer")
        }

    private fun get(path: String): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            // Spring 경로 — 로그인 사용자의 Cognito ID Token Bearer 만 부착한다.
            // (게스트는 이 기능을 호출하지 않으므로 게스트 헤더는 두지 않는다.)
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15_000
            // Gemini 생성 시간(백엔드 max_token↑ + thinking, 2026-06-06) 대비 90s.
            readTimeout = 90_000
        }

        try {
            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText().orEmpty()
            }

            if (code !in 200..299) {
                throw Exception("HTTP $code: $text")
            }

            return text
        } finally {
            conn.disconnect()
        }
    }
}
