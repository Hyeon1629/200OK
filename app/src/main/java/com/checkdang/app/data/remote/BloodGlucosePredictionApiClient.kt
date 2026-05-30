package com.checkdang.app.data.remote

import android.util.Log
import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ML 혈당 예측 API 클라이언트 (FastAPI · `api.checkdang.xyz`).
 *
 * 백엔드 명세(2026-05-29, kgh): 24시간 × 5분 간격 혈당 288개를 입력으로
 * 향후 3시간(5분 간격 36개) 혈당을 예측한다.
 *  - POST `/ai/predict/blood-glucose/{user_id}?date=` — 예측 실행 + 자동 저장
 *  - GET  `/ai/predictions/{user_id}/latest?date=`    — 최신 예측 1건
 *  - GET  `/ai/predictions/{user_id}?date=`           — 날짜별 예측 이력
 *
 * 입력 288개는 앱이 직접 보유하지 않으므로(수동 입력 위주) **body 를 생략**해
 * 백엔드가 DynamoDB `blood_glucose_record` 에서 288개를 자동 조회하게 한다(명세 "방식 B").
 * 앱이 CGM 288개를 갖게 되면 [predict] 에 glucose 배열 body 만 추가하면 "방식 A" 가 된다.
 *
 * 인증 헤더는 다른 FastAPI 클라이언트와 동일 패턴(Cognito ID Token Bearer / 게스트 헤더).
 */
object BloodGlucosePredictionApiClient {

    private const val TAG = "PredictionApi"
    private const val BASE_URL = "https://api.checkdang.xyz"

    /**
     * 예측 실행 + 자동 저장. body 생략 → 백엔드가 DynamoDB 에서 해당 날짜 혈당 288개 조회.
     * @throws PredictionApiException 422(데이터 288개 미달) / 502(예측 서비스 연결 실패) 등.
     */
    suspend fun predict(userId: String, date: String): BloodGlucosePrediction =
        withContext(Dispatchers.IO) {
            val text = request("POST", "/ai/predict/blood-glucose/$userId?date=$date", body = null)
            parse(JSONObject(text))
        }

    /** 해당 날짜의 최신 예측 1건. 예측 기록이 없으면(404) null. */
    suspend fun latest(userId: String, date: String): BloodGlucosePrediction? =
        withContext(Dispatchers.IO) {
            try {
                val text = request("GET", "/ai/predictions/$userId/latest?date=$date", body = null)
                parse(JSONObject(text))
            } catch (e: PredictionApiException) {
                if (e.code == 404) null else throw e
            }
        }

    /** 해당 날짜의 예측 이력(최신순). 없으면 빈 리스트. */
    suspend fun history(userId: String, date: String): List<BloodGlucosePrediction> =
        withContext(Dispatchers.IO) {
            val text = request("GET", "/ai/predictions/$userId?date=$date", body = null)
            val arr = JSONArray(text)
            (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        }

    private fun parse(o: JSONObject): BloodGlucosePrediction {
        val arr = o.getJSONArray("predictions")
        val preds = (0 until arr.length()).map { arr.getDouble(it).toFloat() }
        return BloodGlucosePrediction(
            predictions     = preds,
            horizonMinutes  = o.optInt("horizon_minutes", 180),
            intervalMinutes = o.optInt("interval_minutes", 5),
            source          = o.optString("source", ""),
            predictedAt     = o.optString("predicted_at", ""),
            modelVersion    = if (o.isNull("model_version")) null else o.optString("model_version", null),
        )
    }

    /**
     * 공통 HTTP. 2xx 면 본문 문자열 반환, 그 외엔 응답 body 의 `detail` 을 담아
     * [PredictionApiException] 으로 던진다(422/404/502 분기는 호출 측 책임).
     */
    private fun request(method: String, path: String, body: JSONObject?): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            // 로그인 사용자: Cognito ID Token Bearer. 게스트: X-Guest-Identity-Id(백엔드 GuestIdentityFilter).
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            SessionHolder.guestIdentityId
                ?.takeIf { SessionHolder.isGuest }
                ?.let { setRequestProperty("X-Guest-Identity-Id", it) }
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

/** 예측 응답 1건 (predict / latest / history 공통). */
data class BloodGlucosePrediction(
    val predictions: List<Float>,   // 향후 예측 혈당 36개 (mg/dL, 5분 간격)
    val horizonMinutes: Int,        // 예측 지평 (180)
    val intervalMinutes: Int,       // 예측 간격 (5)
    val source: String,             // "body"(직접 전송) / "db"(DynamoDB 조회)
    val predictedAt: String,        // 예측 실행 시각 (예: "2026-05-29T15:00:00")
    val modelVersion: String? = null,
)

/** 예측 API 비정상 응답. [code] 로 422(데이터 부족)/404(기록 없음)/502(서비스 장애) 를 구분한다. */
class PredictionApiException(val code: Int, val detail: String) : Exception("HTTP $code: $detail")
