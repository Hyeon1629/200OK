package com.checkdang.app.data.remote

import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiAdviceApiClient {
    // 실기기 검증: `adb reverse tcp:8080 tcp:8080` 으로 폰 localhost:8080 → 개발 PC 8080 포워딩.
    // 에뮬레이터 단독 검증 시에는 "http://10.0.2.2:8080" 사용. 배포 시 실제 백엔드 주소로 교체.
    private const val BASE_URL = "http://127.0.0.1:8080"

    suspend fun getDietAdviceForDemo(): String =
        withContext(Dispatchers.IO) {
            val text = get("/api/ai/demo-diet-advice")
            JSONObject(text).getString("answer")
        }

    private fun get(path: String): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            // FastAPI 로 가는 모든 요청에 인증 헤더 부착(HealthSyncApiClient 와 동일 패턴).
            // 로그인 사용자: Cognito ID Token Bearer. 게스트: X-Guest-Identity-Id(백엔드 GuestIdentityFilter).
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            SessionHolder.guestIdentityId
                ?.takeIf { SessionHolder.isGuest }
                ?.let { setRequestProperty("X-Guest-Identity-Id", it) }
            connectTimeout = 15_000
            readTimeout = 60_000
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
