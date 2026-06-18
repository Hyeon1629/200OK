package com.checkdang.app.data.remote

import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Play 결제 검증 백엔드 클라이언트(2026-05-24 추가).
 *
 * BillingRepository 의 `handlePurchase` 흐름에서 acknowledge 와 별개로 호출.
 * 백엔드(`PaymentController.googleVerify`)는 purchaseToken 을 Google Play Developer API 로
 * 검증한 뒤 `payment_records` DynamoDB 저장 + `users.isPremium` 갱신까지 처리한다.
 *
 * 요청 body 는 2개 필드만:
 *   `{ "purchaseToken": "<google_token>", "subscriptionId": "premium_monthly" }`
 * packageName 은 백엔드 환경변수에서 읽으므로 클라이언트가 보낼 필요 없음.
 */
data class VerifyResult(
    val isPremium: Boolean,
    val premiumExpiresAt: String?,
    val message: String?
)

data class KakaoReadyResult(
    val nextRedirectAppUrl: String,
    val orderId: String
)

data class KakaoApproveResult(
    val isPremium: Boolean
)

object PaymentApiClient {

    private const val BASE_URL = "https://api.checkdang.xyz"

    suspend fun kakaoReady(subscriptionId: String): KakaoReadyResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("subscriptionId", subscriptionId) }
        val text = post("/api/payment/kakao/ready", body)
        val root = JSONObject(text)
        val data = root.optJSONObject("data") ?: root
        KakaoReadyResult(
            nextRedirectAppUrl = data.getString("nextRedirectAppUrl"),
            orderId            = data.getString("orderId")
        )
    }

    suspend fun kakaoApprove(orderId: String, pgToken: String): KakaoApproveResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("orderId", orderId)
            put("pgToken", pgToken)
        }
        val text = post("/api/payment/kakao/approve", body)
        val root = JSONObject(text)
        val data = root.optJSONObject("data") ?: root
        KakaoApproveResult(isPremium = data.optBoolean("isPremium", false))
    }

    suspend fun verifyGooglePurchase(
        purchaseToken: String,
        subscriptionId: String
    ): VerifyResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("purchaseToken",  purchaseToken)
            put("subscriptionId", subscriptionId)
        }
        val text = post("/api/payment/google/verify", body)
        val root = JSONObject(text)
        val data = root.optJSONObject("data")
        VerifyResult(
            isPremium        = data?.optBoolean("isPremium", false) ?: false,
            premiumExpiresAt = data?.optString("premiumExpiresAt")?.ifEmpty { null },
            message          = root.optString("message").ifEmpty { null }
        )
    }

    suspend fun resetMockIsPremium(): Unit = withContext(Dispatchers.IO) {
        post("/api/payment/kakao/mock/reset", JSONObject())
    }

    private fun post(path: String, body: JSONObject): String {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            SessionHolder.accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15_000
            readTimeout    = 15_000
            doOutput       = true
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            return if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                val msg = runCatching { JSONObject(err).optString("message", "") }
                    .getOrDefault("").ifEmpty { "결제 검증 실패 ($code)" }
                throw Exception(msg)
            }
        } finally {
            conn.disconnect()
        }
    }
}
