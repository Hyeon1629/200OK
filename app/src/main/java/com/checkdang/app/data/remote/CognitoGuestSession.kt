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
            session.identityIdResult
        }.onSuccess { r ->
            val id = r.value
            if (id != null) {
                SessionHolder.guestIdentityId = id
                Log.i(TAG, "게스트 Identity ID 확보")
            } else {
                // type=FAILURE 일 때 r.error 에 진짜 원인. Identity Pool unauthenticated 비활성/
                // role 미할당/네트워크 어느 쪽인지는 메시지·스택으로 판별.
                Log.w(TAG, "Identity ID 미발급 (type=${r.type}): ${r.error?.message}", r.error)
            }
        }.onFailure { Log.w(TAG, "Identity ID 발급 실패: ${it.message}", it) }
    }
}
