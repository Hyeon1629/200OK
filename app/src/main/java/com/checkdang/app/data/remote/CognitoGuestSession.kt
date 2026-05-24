package com.checkdang.app.data.remote

import android.util.Log
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.kotlin.core.Amplify
import com.checkdang.app.data.mock.SessionHolder

/**
 * 게스트 사용자의 Cognito Identity Pool ID(unauthenticated identity) 를 발급/캐시하는 유틸.
 *
 * 백엔드는 게스트 보호 API 호출 시 `X-Guest-Identity-Id` 헤더로 받아 `DescribeIdentity` 로
 * 검증 후 `ROLE_GUEST` 부여한다. 즉 이 ID 가 없으면 게스트 보호 API 호출은 401.
 *
 * 첫 호출 시 SDK 가 Cognito Identity Pool 에 unauthenticated identity 를 요청해 받고,
 * 이후에는 SDK 내부 캐시에서 즉시 반환한다.
 */
object CognitoGuestSession {

    private const val TAG = "CognitoGuest"

    /** [SessionHolder.guestIdentityId] 에 발급 결과를 저장. 실패 시 null 유지. */
    suspend fun ensureIdentityId() {
        runCatching {
            val session = Amplify.Auth.fetchAuthSession() as AWSCognitoAuthSession
            // AuthSessionResult<String>.value 는 Success 일 때만 non-null. Failure 면 null.
            session.identityIdResult.value
        }.onSuccess { id: String? ->
            if (id != null) {
                SessionHolder.guestIdentityId = id
                Log.i(TAG, "게스트 Identity ID 확보")
            } else {
                Log.w(TAG, "Identity ID 결과가 비어있음 — Identity Pool 미지원 단말?")
            }
        }.onFailure { Log.w(TAG, "Identity ID 발급 실패: ${it.message}") }
    }
}
