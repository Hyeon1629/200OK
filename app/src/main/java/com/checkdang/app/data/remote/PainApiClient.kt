package com.checkdang.app.data.remote

import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.MusclePart
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.data.model.PainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PainApiClient {

    private const val BASE_URL = "https://two00ok-8r84.onrender.com"

    private fun request(
        path: String,
        method: String,
        accessToken: String,
        body: JSONObject? = null
    ): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = body != null
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            return if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                val message = runCatching { JSONObject(err).optString("message", "") }
                    .getOrDefault("").ifEmpty { "서버 오류 ($code)" }
                throw Exception(message)
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun savePainRecord(accessToken: String, record: PainRecord): PainRecord =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("bodyPart", record.bodyPart.name)
                put("muscleName", record.musclePart?.name ?: "")
                put("painType", record.painTypes.joinToString(",") { it.name })
                put("painIntensity", record.intensity)
                put("recordedAt", record.recordedAt)
            }
            val text = request("/api/pain", "POST", accessToken, body)
            parsePainRecord(text, record) ?: record
        }

    private fun parsePainRecord(text: String, original: PainRecord): PainRecord? {
        return runCatching {
            val json = JSONObject(text)
            val src = json.optJSONObject("data") ?: json
            original.copy(
                id = src.optLong("id", 0L).toString().takeIf { it != "0" } ?: original.id
            )
        }.getOrNull()
    }
}
