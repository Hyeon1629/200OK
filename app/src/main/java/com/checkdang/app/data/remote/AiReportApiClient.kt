package com.checkdang.app.data.remote

import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini **종합 리포트** API 클라이언트.
 *
 * 종합 리포트 = 혈당 + 라이프스타일(운동/식사/수면) + 통증을 묶어 Gemini 가 생성하는
 * 자연어 건강 리포트. 경로는 `/api/` 프리픽스 → Spring 라우팅(AiAdviceApiClient 와 동일 패턴).
 *
 * 인증: 로그인 사용자 Bearer 전용. 게스트는 호출하지 않는다(AI/FastAPI 게스트 미지원 정책).
 *
 * TODO(report): 아래 3가지는 **백엔드(kgh) 스키마 회신 후 확정** — 현재는 가정값(provisional).
 *   1) 엔드포인트 경로/메서드          (가정: GET /api/ai/comprehensive-report)
 *   2) 요청 파라미터(기간/날짜 등)      (가정: 없음 — 토큰으로 userId 추출)
 *   3) 응답 필드명                      (가정: { "report": "<markdown>" })
 *  회신 오면 [PATH] · [request] · [parse] 만 교체하면 된다.
 */
object AiReportApiClient {

    private const val BASE_URL = "https://api.checkdang.xyz"
    private const val PATH = "/api/ai/comprehensive-report"   // TODO(report): 백엔드 확정 경로로 교체

    /** 종합 리포트 마크다운 본문을 반환. 실패 시 예외 → 호출 측(ViewModel)이 Error 상태로 표시. */
    suspend fun getComprehensiveReport(): String =
        withContext(Dispatchers.IO) {
            val text = get(PATH)
            // TODO(report): 응답 필드명 백엔드 확정 후 교체. 구조화 JSON 이면 별도 모델로 파싱.
            JSONObject(text).getString("report")
        }

    private fun get(path: String): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            // Spring 경로 — 로그인 사용자 Cognito ID Token Bearer 만 부착(게스트는 호출 안 함).
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15_000
            readTimeout    = 60_000   // Gemini 추론이 길 수 있어 넉넉히(식단조언과 동일)
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
