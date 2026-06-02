package com.checkdang.app.push

import android.content.Context
import android.util.Log

/**
 * FCM 등록 토큰 보관 + (TODO) 백엔드 등록.
 *
 * 백엔드(checkdang-65238)가 특정 기기로 푸시를 보내려면 이 토큰을 서버에 등록해야 한다.
 * 등록 엔드포인트 계약(URL/메서드/인증/페이로드)이 **아직 미확정**이라, 현재는 토큰을
 * 로컬 캐시 + logcat 노출만 한다(백엔드가 이 값으로 콘솔/서버에서 테스트 푸시 가능).
 *
 * 엔드포인트 확정 시 [register] 의 TODO 자리에 업로드 호출만 추가하면 된다
 * (게스트 미지원 — 로그인 사용자 Cognito Bearer 로 등록하는 패턴이 자연스럽다).
 */
object PushTokenStore {

    private const val PREFS = "push_token"
    private const val KEY_TOKEN = "fcm_token"
    private const val KEY_SYNCED = "synced" // 백엔드 등록 완료 여부

    /** onNewToken / 앱 시작 시 호출. 토큰을 캐시하고 (미동기화면) 백엔드 등록을 시도. */
    fun register(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (token != prefs.getString(KEY_TOKEN, null)) {
            prefs.edit().putString(KEY_TOKEN, token).putBoolean(KEY_SYNCED, false).apply()
        }
        Log.i("FcmToken", token) // TODO(diag): 백엔드 등록 연동 후 제거

        // TODO(push-backend): 토큰 등록 엔드포인트 확정 시 여기서 업로드.
        //   예) POST /api/users/me/fcm-token  (Cognito Bearer)  body { "token": token }
        //   성공 시 prefs.edit().putBoolean(KEY_SYNCED, true).apply()
    }

    /** 캐시된 토큰(없으면 null). 백엔드 등록 연동 시 재사용. */
    fun cached(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)
}
