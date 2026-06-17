package com.checkdang.app.data.remote

import android.util.Log
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.ExerciseSession
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.model.HeartRateSample
import com.checkdang.app.data.model.MealItem
import com.checkdang.app.data.model.SleepSummary
import com.checkdang.app.util.MealTiming
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

    /**
     * DynamoDB PK 가 `{user_id}#{date}`, SK 가 `source_id` 로 변경(2026-05-24 백엔드).
     * date 는 path query, source_id 는 body. 같은 source_id 재전송은 putItem upsert 로 1건 유지.
     *
     * @return 전송에 성공한 record 목록. 호출 측이 [GlucoseSyncStore.markPushed] 로
     *         재전송 방지 기록을 남기는 데 사용한다.
     */
    suspend fun pushGlucose(records: List<GlucoseRecord>): List<GlucoseRecord> =
        withContext(Dispatchers.IO) {
            val userId = SessionHolder.userId ?: return@withContext emptyList()
            val sent = mutableListOf<GlucoseRecord>()
            records.forEach { r ->
                runCatching {
                    val zoned = Instant.ofEpochMilli(r.measuredAt).atZone(KST)
                    val dateKst      = zoned.toLocalDate().toString()        // 2026-05-19
                    val timestampKst = zoned.toLocalDateTime().toString()    // 2026-05-19T11:36:59
                    val body = JSONObject().apply {
                        put("source_id",   r.id)
                        put("level",       r.value)
                        put("meal_timing", mapMealTiming(r.timing))
                        put("timestamp",   timestampKst)
                        r.memo?.let { put("memo", it) }
                    }
                    post("/blood-glucose/$userId?date=$dateKst", body)
                }.onSuccess { sent += r }
                    .onFailure { Log.w(TAG, "pushGlucose record ${r.id} failed: ${it.message}") }
            }
            sent
        }

    // ── FastAPI: 일별 걸음 + 활동 칼로리 ─────────────────────────────────────

    /**
     * 하루 1건의 step_calorie record 송신.
     * timestamp 는 측정 시각(현재 시각 또는 해당 일의 끝)으로 채운다.
     *
     * DynamoDB PK `{user_id}#{date}` + SK `source_id` (2026-05-24 백엔드).
     * source_id 는 `"{deviceId}-{date}"` — 한 단말이 하루에 1건만 가지므로 재전송도 upsert.
     */
    suspend fun pushStepCalorie(
        date: java.time.LocalDate,
        stepCount: Int,
        calorie: Double,
        deviceId: String
    ) = withContext(Dispatchers.IO) {
        val userId = SessionHolder.userId ?: return@withContext
        val nowKst = java.time.LocalDateTime.now(KST).toString()
        val body = JSONObject().apply {
            put("source_id",  "$deviceId-$date")
            put("timestamp",  nowKst)
            put("step_count", stepCount)
            put("calorie",    calorie)
            put("device_id",  deviceId)
        }
        post("/step-calorie/$userId?date=$date", body)
    }

    // ── FastAPI: 심박수 시계열 (샘플 별 POST) ────────────────────────────────

    /**
     * 심박수 샘플을 1건씩 송신. 한 record 실패가 다음 record 송신을 막지 않도록 record 별 runCatching.
     *
     * DynamoDB PK `{user_id}#{date}` + SK `source_id` (2026-05-24 백엔드).
     * source_id 는 `"{deviceId}-{timestamp_millis}"` — 같은 단말이 같은 epoch ms 의 샘플을
     * 재전송해도 upsert 로 1건 유지(서버 멱등).
     */
    suspend fun pushHeartRates(
        date: java.time.LocalDate,
        samples: List<HeartRateSample>,
        deviceId: String
    ) = withContext(Dispatchers.IO) {
        val userId = SessionHolder.userId ?: return@withContext
        if (samples.isEmpty()) return@withContext
        samples.forEach { s ->
            runCatching {
                val ts = Instant.ofEpochMilli(s.timestamp).atZone(KST).toLocalDateTime().toString()
                val body = JSONObject().apply {
                    put("source_id", "$deviceId-${s.timestamp}")
                    put("timestamp", ts)
                    put("bpm",       s.bpm)
                    put("device_id", deviceId)
                }
                post("/heart-rate/$userId?date=$date", body)
            }.onFailure { Log.w(TAG, "pushHeartRate(ts=${s.timestamp}) failed: ${it.message}") }
        }
    }

    /** 우리 7종 MealTiming → 백엔드 4종 enum 매핑. OTHER 는 백엔드에 대응 값 없어 FASTING 으로 임시. */
    private fun mapMealTiming(timing: MealTiming): String = when (timing) {
        MealTiming.FASTING        -> "FASTING"
        MealTiming.PRE_MEAL       -> "BEFORE_MEAL"
        MealTiming.POST_MEAL_30M,
        MealTiming.POST_MEAL_1H,
        MealTiming.POST_MEAL_2H   -> "AFTER_MEAL"
        MealTiming.BEFORE_SLEEP   -> "BEDTIME"
        MealTiming.OTHER          -> "FASTING"
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

    // ── HTTP (Cognito ID Token Bearer 자동 부착. 로그인 사용자 전용) ──
    //
    // Spring(`/api/samsung-health/*`) 은 게스트 필터가 있으나 게스트는 `/api/home/**` 만 허용되고,
    // FastAPI(`/blood-glucose|/step-calorie|/heart-rate`) 는 게스트를 지원하지 않는다.
    // 따라서 게스트는 호출 측(ViewModel)에서 차단하며 여기서는 Bearer 만 부착한다.

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
