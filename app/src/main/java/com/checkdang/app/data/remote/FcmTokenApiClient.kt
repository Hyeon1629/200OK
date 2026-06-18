package com.checkdang.app.data.remote

import com.checkdang.app.data.mock.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * FCM 등록 토큰 업로드 클라이언트 (Spring · `api.checkdang.xyz`).
 *
 * 엔드포인트: `PATCH /api/auth/fcm-token`  body `{ "fcmToken": "<token>" }`  (Cognito Bearer)
 * 응답: Spring 공통 래퍼 `{ success, data, message }` (data = void).
 *
 * 로그인 사용자 전용 — 게스트는 토큰을 등록하지 않는다(호출 측 [com.checkdang.app.push.PushTokenStore]
 * 가 accessToken 유무로 차단). 백엔드는 이 토큰으로 해당 사용자 기기에 푸시를 발송한다.
 */
object FcmTokenApiClient {

    private const val BASE_URL = "https://api.checkdang.xyz"

    /** 토큰 등록(PATCH). 2xx 면 true, 그 외엔 false(호출 측이 미동기화로 두고 재시도). */
    suspend fun update(token: String): Boolean = withContext(Dispatchers.IO) {
        val accessToken = SessionHolder.accessToken ?: return@withContext false
        val body = JSONObject().put("fcmToken", token)
        val conn = (URL("$BASE_URL/api/auth/fcm-token").openConnection() as HttpURLConnection).apply {
            forcePatchMethod(this)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                true
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                android.util.Log.w("FcmToken", "등록 실패 code=$code method=${conn.requestMethod} body=$err")
                false
            }
        } catch (e: Exception) {
            android.util.Log.w("FcmToken", "등록 실패(예외)", e)
            false
        } finally {
            conn.disconnect()
        }
    }

    /**
     * HttpURLConnection 은 PATCH 를 허용 메서드 목록에서 제외해 [HttpURLConnection.setRequestMethod]
     * 가 ProtocolException 을 던진다. 베이스 `method` 필드를 리플렉션으로 직접 세팅한다.
     *
     * HTTPS 연결(api.checkdang.xyz)은 Android 가 `DelegatingHttpsURLConnection` 래퍼로 반환하고
     * 실제 요청은 내부 `delegate`(HttpURLConnectionImpl)가 만든다. 래퍼의 `method` 만 바꾸면
     * delegate 는 그대로라 반영되지 않으므로, delegate 를 꺼내 그 `method` 필드에 세팅한다.
     * 모두 실패하면 POST + override 헤더로 폴백.
     */
    private fun forcePatchMethod(conn: HttpURLConnection) {
        try {
            // HTTPS 래퍼면 실제 요청을 만드는 내부 delegate 를 대상으로.
            val target: Any = if (conn is HttpsURLConnection) {
                conn.javaClass.getDeclaredField("delegate").apply { isAccessible = true }.get(conn)
            } else {
                conn
            }
            val field = HttpURLConnection::class.java.getDeclaredField("method")
            field.isAccessible = true
            field.set(target, "PATCH")
        } catch (e: Exception) {
            android.util.Log.w("FcmToken", "PATCH 리플렉션 실패 → POST+override 폴백", e)
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        }
    }
}
