package com.checkdang.app.data.remote

import android.util.Log
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.ExerciseSession
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.MealItem
import com.checkdang.app.data.model.SleepSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 라이프스타일·혈당 데이터의 백엔드 DB 저장 전용 클라이언트.
 *
 * 라우팅: api.checkdang.xyz 단일 base URL 에서 path 기반으로 두 서비스로 분기
 *  - `/api/samsung-health/{exercises|diets|sleeps}` → Spring Boot (Bearer JWT 필요)
 *  - `/blood-glucose/{user_id}` → FastAPI
 *
 * 실패는 호출 측이 runCatching 으로 감싸 화면 동작이 깨지지 않도록 한다.
 */
object HealthSyncApiClient {

    private const val TAG = "HealthSyncApi"
    private const val BASE_URL = "https://api.checkdang.xyz"
    private val KST = ZoneId.of("Asia/Seoul")

    // ── Spring Boot: 운동 ─────────────────────────────────────────────────────

    suspend fun pushExercises(sessions: List<ExerciseSession>) = withContext(Dispatchers.IO) {
        if (sessions.isEmpty()) return@withContext
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(JSONObject().apply {
                put("exerciseName", s.type)
                put("duration",     s.durationMin.toLong())
                put("recordedAt",   koreanClockToIso(s.startedAt))
            })
        }
        post("/api/samsung-health/exercises", arr)
    }

    // ── Spring Boot: 식사 ─────────────────────────────────────────────────────

    suspend fun pushDiets(items: List<MealItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val arr = JSONArray()
        items.forEach { m ->
            arr.put(JSONObject().apply {
                put("foodName",   m.name)
                put("mealType",   mealTypeToEnum(m.type))
                put("recordedAt", anyClockToIso(m.time))
                put("calories",   m.kcal.toDouble())
            })
        }
        post("/api/samsung-health/diets", arr)
    }

    // ── Spring Boot: 수면 ─────────────────────────────────────────────────────

    suspend fun pushSleep(summary: SleepSummary) = withContext(Dispatchers.IO) {
        val bedIso  = anyClockToIso(summary.bedtime,  shiftDay = -1)  // 취침은 전날 밤이 일반적
        val wakeIso = anyClockToIso(summary.wakeTime, shiftDay = 0)
        val durationMin = (summary.totalHours * 60).toLong()
        val arr = JSONArray().put(JSONObject().apply {
            put("sleepTime", bedIso)
            put("wakeTime",  wakeIso)
            put("duration",  durationMin)
            put("quality",   summary.efficiency.toDouble())
            put("stages",    JSONArray())   // stage 단위 시각 데이터 없음 → 빈 배열
        })
        post("/api/samsung-health/sleeps", arr)
    }

    // ── FastAPI: 혈당 (record 별 POST) ───────────────────────────────────────

    suspend fun pushGlucose(records: List<GlucoseRecord>) = withContext(Dispatchers.IO) {
        val userId = SessionHolder.userId ?: return@withContext
        records.forEach { r ->
            val body = JSONObject().apply {
                put("value",       r.value)
                put("timing",      r.timing.name)
                put("measured_at", Instant.ofEpochMilli(r.measuredAt).toString())
                r.memo?.let { put("memo", it) }
            }
            runCatching { post("/blood-glucose/$userId", body) }
                .onFailure { Log.w(TAG, "pushGlucose record ${r.id} failed: ${it.message}") }
        }
    }

    // ── 변환 헬퍼 ─────────────────────────────────────────────────────────────

    private fun mealTypeToEnum(korean: String): String = when (korean) {
        "아침" -> "BREAKFAST"
        "점심" -> "LUNCH"
        "저녁" -> "DINNER"
        "간식" -> "SNACK"
        else   -> "UNKNOWN"
    }

    /** "오전 7:30" / "오후 1:45" 같은 한글 시각을 오늘 KST 기준 ISO date-time 으로 변환. 실패 시 현재 시각. */
    private fun koreanClockToIso(korean: String, shiftDay: Int = 0): String = runCatching {
        val isPm = listOf("오후", "PM", "pm").any { it in korean }
        val isAm = listOf("오전", "AM", "am").any { it in korean }
        val cleaned = korean.replace(Regex("[오전후AMPMampm\\s]"), "")
        val parts = cleaned.split(":")
        val rawH = parts[0].toInt()
        val mm   = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val h24 = when {
            isPm && rawH < 12  -> rawH + 12
            isAm && rawH == 12 -> 0
            else               -> rawH
        }
        LocalDate.now(KST).plusDays(shiftDay.toLong())
            .atTime(LocalTime.of(h24, mm)).atZone(KST).toInstant().toString()
    }.getOrElse { Instant.now().toString() }

    /** "23:42" 형식의 HH:mm, 또는 한글 시각을 모두 처리. */
    private fun anyClockToIso(clock: String, shiftDay: Int = 0): String = runCatching {
        if (clock.contains("오전") || clock.contains("오후")) {
            return koreanClockToIso(clock, shiftDay)
        }
        val parts = clock.split(":")
        val h = parts[0].toInt()
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        LocalDate.now(KST).plusDays(shiftDay.toLong())
            .atTime(LocalTime.of(h, m)).atZone(KST).toInstant().toString()
    }.getOrElse { Instant.now().toString() }

    // ── HTTP (Spring Bearer JWT 자동 부착, FastAPI 도 동일 헤더 송신 — 불필요 시 무시됨) ──

    private fun post(path: String, body: Any) {
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
            instanceFollowRedirects = false
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                Log.i(TAG, "POST $path → $code OK")
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw Exception("HTTP $code: ${err.take(200)}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
