package com.checkdang.app.push

import android.content.Context
import android.util.Log
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.remote.FcmTokenApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCM 등록 토큰 보관 + 백엔드 등록.
 *
 * 백엔드(checkdang-65238)가 특정 기기로 푸시를 보내려면 이 토큰을 서버에 등록해야 한다.
 * 엔드포인트: `PATCH /api/auth/fcm-token` (Cognito Bearer) — [FcmTokenApiClient].
 *
 * 토큰은 앱 시작/갱신 시 캐시되지만, 로그인 사용자만 등록한다. 앱 콜드 스타트 시점엔 아직
 * 세션이 없을 수 있어(accessToken=null) 업로드를 건너뛰고, 로그인 완료 후 [syncCachedToken]
 * 호출로 등록을 마무리한다. 등록 성공은 "어떤 userId 로 등록했는지"(KEY_SYNCED_USER)로 추적해
 * 토큰 변경·재로그인(다른 사용자) 시 자동 재등록되게 한다.
 */
object PushTokenStore {

    private const val PREFS = "push_token"
    private const val KEY_TOKEN = "fcm_token"
    private const val KEY_SYNCED_USER = "synced_user" // 현재 토큰을 등록한 userId

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** onNewToken / 앱 시작 시 호출. 토큰을 캐시하고 (로그인 상태면) 백엔드 등록을 시도. */
    fun register(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (token != prefs.getString(KEY_TOKEN, null)) {
            // 토큰이 바뀌면 이전 등록 상태를 무효화(재등록 유도).
            prefs.edit().putString(KEY_TOKEN, token).remove(KEY_SYNCED_USER).apply()
        }
        Log.i("FcmToken", token) // TODO(diag): 안정화 후 제거
        syncCachedToken(context)
    }

    /**
     * 캐시된 토큰을 백엔드에 등록(필요 시). 로그인 완료 직후에도 호출해 콜드 스타트 시 건너뛴
     * 등록을 마무리한다. 미로그인/게스트(accessToken·userId 없음)거나 이미 현재 userId 로
     * 등록된 토큰이면 아무 것도 하지 않는다.
     */
    fun syncCachedToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        val userId = SessionHolder.userId ?: return // 미로그인/게스트 — 로그인 후 재시도
        if (SessionHolder.accessToken == null) return
        if (prefs.getString(KEY_SYNCED_USER, null) == userId) return // 이미 등록됨

        scope.launch {
            if (FcmTokenApiClient.update(token)) {
                prefs.edit().putString(KEY_SYNCED_USER, userId).apply()
                Log.i("FcmToken", "백엔드 등록 성공 (userId=$userId)")
            } else {
                Log.w("FcmToken", "백엔드 등록 실패 — 다음 시도에 재등록")
            }
        }
    }

    /** 캐시된 토큰(없으면 null). */
    fun cached(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)
}
