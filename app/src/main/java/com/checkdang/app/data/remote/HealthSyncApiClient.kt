package com.checkdang.app.data.remote

import android.util.Log
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.ExerciseSummary
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.MealSummary
import com.checkdang.app.data.model.SleepSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 라이프스타일(운동/식사/수면) 및 혈당 데이터의 백엔드 DB 저장 전용 클라이언트.
 *
 * - 동기화(Health Connect / Samsung Health → 앱 메모리)가 완료된 직후 호출되어
 *   서버 DB 에 push 한다.
 * - 실패는 호출 측에서 runCatching 으로 감싸 화면 동작을 깨지 않도록 한다.
 */
object HealthSyncApiClient {

    private const val TAG = "HealthSyncApi"
    private const val BASE_URL = "https://api.checkdang.xyz"

    // ── 라이프스타일 (운동 / 식사 / 수면) ──────────────────────────────────────

    suspend fun pushLifestyle(
        exercise: ExerciseSummary?,
        meal: MealSummary?,
        sleep: SleepSummary?,
        source: String
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            SessionHolder.userId?.let { put("userId", it) }
            put("source", source)
            put("recordedAt", System.currentTimeMillis())
            exercise?.let { put("exercise", exerciseToJson(it)) }
            meal?.let     { put("meal",     mealToJson(it)) }
            sleep?.let    { put("sleep",    sleepToJson(it)) }
        }
        post("/api/health/lifestyle", body)
        Unit
    }

    // ── 혈당 레코드 일괄 저장 ─────────────────────────────────────────────────

    suspend fun pushGlucose(records: List<GlucoseRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        val body = JSONObject().apply {
            SessionHolder.userId?.let { put("userId", it) }
            val arr = JSONArray()
            records.forEach { arr.put(glucoseToJson(it)) }
            put("records", arr)
        }
        post("/api/health/glucose", body)
        Unit
    }

    // ── JSON 직렬화 헬퍼 ──────────────────────────────────────────────────────

    private fun exerciseToJson(s: ExerciseSummary) = JSONObject().apply {
        put("totalMinutes",  s.totalMinutes)
        put("goalMinutes",   s.goalMinutes)
        put("totalCalories", s.totalCalories)
        val sessions = JSONArray()
        s.sessions.forEach { session ->
            sessions.put(JSONObject().apply {
                put("type",        session.type)
                put("durationMin", session.durationMin)
                put("calories",    session.calories)
                put("startedAt",   session.startedAt)
            })
        }
        put("sessions", sessions)
    }

    private fun mealToJson(s: MealSummary) = JSONObject().apply {
        put("totalKcal", s.totalKcal)
        put("goalKcal",  s.goalKcal)
        put("carbsG",    s.carbsG)
        put("proteinG",  s.proteinG)
        put("fatG",      s.fatG)
        val items = JSONArray()
        s.meals.forEach { item ->
            items.put(JSONObject().apply {
                put("type", item.type)
                put("name", item.name)
                put("kcal", item.kcal)
                put("time", item.time)
            })
        }
        put("meals", items)
    }

    private fun sleepToJson(s: SleepSummary) = JSONObject().apply {
        put("totalHours", s.totalHours.toDouble())
        put("efficiency", s.efficiency)
        put("deepHours",  s.deepHours.toDouble())
        put("lightHours", s.lightHours.toDouble())
        put("remHours",   s.remHours.toDouble())
        put("bedtime",    s.bedtime)
        put("wakeTime",   s.wakeTime)
    }

    private fun glucoseToJson(r: GlucoseRecord) = JSONObject().apply {
        put("id",         r.id)
        put("value",      r.value)
        put("timing",     r.timing.name)
        put("measuredAt", r.measuredAt)
        r.memo?.let { put("memo", it) }
    }

    // ── HTTP 호출 (AuthApiClient 와 동일한 패턴 + Bearer 토큰 헤더) ───────────

    private fun post(path: String, body: JSONObject) {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            SessionHolder.accessToken?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            connectTimeout = 15_000
            readTimeout    = 15_000
            doOutput       = true
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                Log.i(TAG, "POST $path → $code OK")
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                val msg = runCatching { JSONObject(err).optString("message", "") }
                    .getOrDefault("").ifEmpty { "서버 오류 ($code)" }
                throw Exception("$code: $msg")
            }
        } finally {
            conn.disconnect()
        }
    }
}
