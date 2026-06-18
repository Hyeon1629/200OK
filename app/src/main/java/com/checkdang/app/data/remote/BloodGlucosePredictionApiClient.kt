package com.checkdang.app.data.remote

import android.util.Log
import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ML 혈당 예측 API 클라이언트 (Spring → FastAPI · `api.checkdang.xyz`).
 *
 * 백엔드 운영 배포 계약(2026-06-16, kgh): 그 날짜 기준 최신 혈당에서 직전 24시간을 입력으로
 * 향후 3시간(5분 간격 36스텝) 혈당을 예측한다. **on-demand 전용** — 호출 시 계산만 하고
 * 저장하지 않으므로 별도 조회/저장(latest/history) API 가 없다.
 *  - POST `/api/ai/predict/blood-glucose?date=YYYY-MM-DD` — 예측 실행 (요청 body 없음)
 *
 * 입력 혈당·carbs·bolus 는 모두 백엔드가 서버에서 모은다(앱이 추가로 보낼 입력 없음).
 * `date` 는 선택(생략 시 오늘)이지만 결정성을 위해 호출 측에서 오늘 날짜를 명시한다.
 *
 * Bearer(Cognito ID 토큰)만 검증한다. 따라서 예측은 로그인 사용자 전용이며,
 * 게스트는 호출 측(GlucoseViewModel)에서 차단한다.
 */
object BloodGlucosePredictionApiClient {

    private const val TAG = "PredictionApi"
    private const val BASE_URL = "https://api.checkdang.xyz"

    /**
     * 예측 실행. 요청 body 없음 → 백엔드가 최신 혈당 직전 24시간(+서버 보관 carbs/bolus)으로 추론.
     * @throws PredictionApiException 422(혈당 측정 부족: 최소 6회 또는 24h 중 12시간 미만) / 401(토큰) 등.
     */
    suspend fun predict(date: String): BloodGlucosePrediction =
        withContext(Dispatchers.IO) {
            val text = request("POST", "/api/ai/predict/blood-glucose?date=$date", body = null)
            parse(JSONObject(text))
        }

    private fun parse(o: JSONObject): BloodGlucosePrediction {
        val arr = o.getJSONArray("predictions")
        val preds = (0 until arr.length()).map { arr.getDouble(it).toFloat() }
        return BloodGlucosePrediction(
            predictions    = preds,
            // 신 계약은 camelCase(horizonMinutes). 구 snake(horizon_minutes)도 폴백으로 허용.
            horizonMinutes = o.optInt("horizonMinutes", o.optInt("horizon_minutes", 180)),
        )
    }

    /**
     * 공통 HTTP. 2xx 면 본문 문자열 반환, 그 외엔 응답 body 의 `detail` 을 담아
     * [PredictionApiException] 으로 던진다(422/401 분기는 호출 측 책임).
     */
    private fun request(method: String, path: String, body: JSONObject?): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            // FastAPI 는 Bearer 토큰만 검증한다(게스트 미지원). 로그인 사용자 전용.
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15_000
            readTimeout    = 60_000   // 모델 추론이 길 수 있어 넉넉히
            instanceFollowRedirects = false
            if (body != null) {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream.bufferedReader().readText()
            }
            val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            // 오류 본문 형식: { "detail": "<한국어 메시지>" }
            val detail = runCatching { JSONObject(err).getString("detail") }
                .getOrDefault(err.take(200))
            Log.w(TAG, "$method $path → $code: $detail")
            throw PredictionApiException(code, detail)
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * 예측 응답 1건. 신 계약 응답은 `predictions`(36개) + `horizonMinutes`(180) 2개뿐.
 * [intervalMinutes] 는 계약상 고정 5분, [predictedAt] 은 on-demand 라 서버가 주지 않으므로
 * 차트 X축은 "+분" 상대 라벨로 표시한다(빈 문자열 → 상대 라벨 폴백).
 */
data class BloodGlucosePrediction(
    val predictions: List<Float>,       // 향후 예측 혈당 36개 (mg/dL, 5분 간격)
    val horizonMinutes: Int,            // 예측 지평 (180)
    val intervalMinutes: Int = 5,       // 예측 간격 (고정 5분)
    val predictedAt: String = "",       // 서버 미제공 → 상대 라벨 사용
)

/** 예측 API 비정상 응답. [code] 로 422(데이터 부족)/401(토큰) 등을 구분한다. */
class PredictionApiException(val code: Int, val detail: String) : Exception("HTTP $code: $detail")
