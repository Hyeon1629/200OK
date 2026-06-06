package com.checkdang.app.data.remote

import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.PainRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 통증 AI 분석 클라이언트 (Spring · `api.checkdang.xyz`).
 *
 * 2단계 호출:
 *  1) POST `/api/pain-records`           — 통증 기록 저장 → painRecordId 발급(201)
 *  2) POST `/api/ai/pain-analysis/{id}`  — 저장된 기록 + 전날~당일 생활데이터로 Gemini 분석
 *
 * 백엔드 체인: 앱 → Spring(PainAnalysisService 수집·위임) → FastAPI `/analyze/pain` → Gemini.analyze_pain.
 * 응답은 Spring 공통 래퍼 `{ success, data, message }`. 분석 결과는 `data.{aiCause, aiFirstAid}`.
 *
 * BodyPart enum 이름·태그(qualityTags/situationTags)는 백엔드 PainRecord 와 동일 어휘로 그대로 직렬화한다.
 * 로그인 사용자 전용(Cognito Bearer) — 게스트는 호출 측(AIAnalysisActivity)에서 차단한다.
 */
data class PainAnalysisResult(
    val painRecordId: Long,
    val aiCause: String,
    val aiFirstAid: String,
)

object PainAnalysisApiClient {

    private const val BASE_URL = "https://api.checkdang.xyz"

    /** 기록 저장(→id) 후 AI 분석까지 수행해 원인/조치를 반환한다. */
    suspend fun analyze(record: PainRecord): PainAnalysisResult = withContext(Dispatchers.IO) {
        val painRecordId = savePainRecord(record)
        requestAnalysis(painRecordId)
    }

    /** 1) 통증 기록 저장 → 생성된 painRecordId. */
    private fun savePainRecord(record: PainRecord): Long {
        val body = JSONObject().apply {
            put("bodyPart", record.bodyPart.name)
            put("intensity", record.intensity)
            put("qualityTags", JSONArray(record.qualityTags))
            put("situationTags", JSONArray(record.situationTags))
        }
        val data = post("/api/pain-records", body)
            ?: throw Exception("통증 기록 저장 응답이 비어 있습니다.")
        val id = data.optLong("id", -1L)
        if (id <= 0L) throw Exception("통증 기록 저장 응답에 id가 없습니다.")
        return id
    }

    /** 2) 저장된 기록으로 AI 분석(원인/조치). */
    private fun requestAnalysis(painRecordId: Long): PainAnalysisResult {
        val data = post("/api/ai/pain-analysis/$painRecordId", null)
            ?: throw Exception("분석 응답이 비어 있습니다.")
        val cause = data.optString("aiCause").ifEmpty { null }
        val firstAid = data.optString("aiFirstAid").ifEmpty { null }
        if (cause == null || firstAid == null) throw Exception("분석 결과가 비어 있습니다.")
        return PainAnalysisResult(
            painRecordId = data.optLong("painRecordId", painRecordId),
            aiCause = cause,
            aiFirstAid = firstAid,
        )
    }

    /**
     * 공통 POST. 2xx 면 ApiResponse 의 `data`(JSONObject, 없을 수 있음)를 반환하고,
     * 그 외엔 `message` 를 담아 예외를 던진다.
     */
    private fun post(path: String, body: JSONObject?): JSONObject? {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Accept", "application/json")
            // Spring 경로 — 로그인 사용자의 Cognito ID Token Bearer 만 부착(게스트 미지원).
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15_000
            readTimeout = 60_000   // 분석 단계는 Gemini 추론이 길 수 있어 넉넉히
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
                val text = conn.inputStream.bufferedReader().readText()
                return JSONObject(text).optJSONObject("data")
            }
            val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            val msg = runCatching { JSONObject(err).optString("message", "") }
                .getOrDefault("").ifEmpty { "통증 분석 실패 ($code)" }
            throw Exception(msg)
        } finally {
            conn.disconnect()
        }
    }
}
