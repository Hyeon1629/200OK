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
 * 현재 리포트 = **식단/수면/운동** 라이프스타일 종합 분석(Gemini 자연어).
 * ⚠️ 혈당·통증은 아직 백엔드 종합 리포트 로직에 미포함(혈당=FastAPI/DynamoDB, 통증=신규 도메인).
 * 경로는 `/api/` 프리픽스 → Spring 라우팅(AiAdviceApiClient 와 동일 패턴).
 *
 * 인증: 로그인 사용자 Bearer 전용. 게스트는 호출하지 않는다(AI/FastAPI 게스트 미지원 정책).
 *
 * 스키마 확정(2026-06-02, 백엔드 kgh 회신):
 *   - 엔드포인트: GET /api/ai/reports/health (선택 ?days=7, 1~30 / 또는 from·to ISO-8601)
 *   - 응답: { from, to, sourceCount, report } — ApiResponse 래퍼 없음(raw). report=마크다운 한 덩어리
 *   - 에러: { success:false, data:null, message:"<한국어>" } (Spring GlobalExceptionHandler)
 *   - 같은 기간 재호출은 캐싱되어 <1초 즉시 반환(첫 호출만 Gemini 5~15초).
 */
object AiReportApiClient {

    private const val BASE_URL = "https://api.checkdang.xyz"
    private const val PATH = "/api/ai/reports/health"

    /** 분석 기간(일). 기본 7. 백엔드 규칙 1~30(초과 시 30 클램프). */
    private const val DEFAULT_DAYS = 7

    /**
     * 종합 리포트 마크다운 본문을 반환. 실패 시 예외 → 호출 측(ViewModel)이 Error 상태로 표시.
     *
     * @param days 분석 기간(일). 토큰만으로 userId 추출 — 그 외 파라미터 불필요.
     */
    suspend fun getComprehensiveReport(days: Int = DEFAULT_DAYS): String =
        withContext(Dispatchers.IO) {
            val text = get("$PATH?days=$days")
            // 응답은 메타(from/to/sourceCount)로 감싸여 있으나 표시 불요 — report 필드만 렌더.
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
