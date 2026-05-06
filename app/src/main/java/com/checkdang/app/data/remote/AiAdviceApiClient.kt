package com.checkdang.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiAdviceApiClient {
    private const val BASE_URL = "http://10.0.2.2:8080"

    suspend fun getDietAdviceForDemo(): String =
        withContext(Dispatchers.IO) {
            val text = get("/api/ai/demo-diet-advice")
            JSONObject(text).getString("answer")
        }

    private fun get(path: String): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 60_000
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
