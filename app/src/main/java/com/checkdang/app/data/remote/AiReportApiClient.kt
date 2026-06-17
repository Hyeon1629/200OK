package com.checkdang.app.data.remote

import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
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
     * 총 시도 횟수(최초 1 + 재시도 1). Gemini API 가 가끔 5xx 를 내려(백엔드 회신 2026-06-03)
     * 일시 오류는 1회 재시도로 흡수한다. 4xx(인증/데이터 부족)는 재시도 무의미 → 즉시 실패.
     */
    private const val MAX_ATTEMPTS = 2

    /** 재시도 전 짧은 백오프(ms). 첫 호출 ~8-10초 대비 무시 가능한 수준. */
    private const val RETRY_DELAY_MS = 800L

    /**
     * 종합 리포트 응답.
     *
     * @param report    Gemini 가 생성한 마크다운 본문(데이터가 있는 항목만 분석).
     * @param sourceCount 항목별 원천 데이터 건수. 0 인 항목은 분석에서 제외돼 화면에
     *                    "○○ 기록이 없습니다" 안내로 표시한다.
     */
    data class ComprehensiveReport(
        val report: String,
        val sourceCount: SourceCount,
    )

    /** 분석에 사용된 항목별 데이터 건수(식단/수면/운동). */
    data class SourceCount(
        val diets: Int,
        val sleeps: Int,
        val exercises: Int,
    )

    /**
     * 종합 리포트(마크다운 본문 + 항목별 건수)를 반환. 실패 시 예외 → 호출 측(ViewModel)이 Error 표시.
     *
     * @param days 분석 기간(일). 토큰만으로 userId 추출 — 그 외 파라미터 불필요.
     */
    suspend fun getComprehensiveReport(days: Int = DEFAULT_DAYS): ComprehensiveReport =
        withContext(Dispatchers.IO) {
            val json = JSONObject(get("$PATH?days=$days"))
            // 메타(from/to) 는 표시 불요. report + sourceCount 만 사용.
            val sc = json.optJSONObject("sourceCount")
            ComprehensiveReport(
                report = json.optString("report").trim(),
                sourceCount = SourceCount(
                    diets = sc?.optInt("diets") ?: 0,
                    sleeps = sc?.optInt("sleeps") ?: 0,
                    exercises = sc?.optInt("exercises") ?: 0,
                ),
            )
        }

    /**
     * 리포트 GET. **5xx(서버/Gemini 일시 오류)·네트워크 오류는 [MAX_ATTEMPTS] 회까지 재시도**한다.
     * 끝까지 5xx 면 `HTTP 5xx` 예외 → ViewModel 이 안내문 표시. 4xx 는 즉시 실패(재시도 무의미).
     */
    private suspend fun get(path: String): String {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val resp = try {
                requestOnce(path)
            } catch (e: IOException) {
                // 연결/읽기 실패(타임아웃 등) — 일시적일 수 있어 재시도 대상.
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS)
                    return@repeat
                }
                throw e
            }
            when (resp.code) {
                in 200..299 -> return resp.body
                in 500..599 -> {
                    // 서버/Gemini 일시 오류 — 1회 재시도. 끝내 실패하면 아래에서 throw.
                    lastError = Exception("HTTP ${resp.code}: ${resp.body}")
                    if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
                }
                else -> throw Exception("HTTP ${resp.code}: ${resp.body}") // 4xx 즉시 실패
            }
        }
        throw lastError ?: Exception("리포트 요청에 실패했어요.")
    }

    /** 단일 HTTP 시도. 응답 코드와 본문(성공=inputStream / 실패=errorStream)을 그대로 반환. */
    private fun requestOnce(path: String): Resp {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            // Spring 경로 — 로그인 사용자 Cognito ID Token Bearer 만 부착(게스트는 호출 안 함).
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15_000
            // 첫 호출 ~8-10초(백엔드 회신 2026-06-03), 출력 길이 고정이라 데이터량 무관 → 60s 면 충분.
            readTimeout = 60_000
        }
        try {
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            return Resp(code, body)
        } finally {
            conn.disconnect()
        }
    }

    /** 1회 HTTP 응답(코드 + 본문). */
    private data class Resp(val code: Int, val body: String)
}
