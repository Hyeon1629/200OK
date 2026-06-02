package com.checkdang.app

import android.app.Application
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.checkdang.app.data.billing.BillingRepository
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.mock.UserStore
import com.checkdang.app.data.remote.GlucoseSyncStore
import com.checkdang.app.data.samsunghealth.SamsungHealthRepository
import com.checkdang.app.push.CheckDangMessagingService
import com.checkdang.app.push.PushTokenStore
import com.checkdang.app.util.GlucoseAlertNotifier
import com.google.firebase.messaging.FirebaseMessaging
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.util.Utility

class CheckDangApplication : Application() {

    lateinit var billingRepository: BillingRepository
        private set

    /**
     * Samsung Health Data SDK Repository — application scope 싱글톤.
     * UI(예: LifestyleFragment) 는 `(application as CheckDangApplication).samsungHealthRepository`
     * 로 접근. SDK 호출은 [SamsungHealthRepository.connect] 가 호출돼야 비로소 시작된다.
     */
    lateinit var samsungHealthRepository: SamsungHealthRepository
        private set

    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, getString(R.string.kakao_app_key))
        Log.d("KakaoKeyHash", Utility.getKeyHash(this))
        UserStore.init(this)
        MockDataProvider.init(this)
        GlucoseSyncStore.init(this)
        GlucoseAlertNotifier.ensureChannel(this)
        CheckDangMessagingService.ensureChannel(this)

        // FCM 등록 토큰 확보 — onNewToken 은 토큰 변경 시에만 호출되므로 시작 시 1회 직접 조회.
        // (현재는 캐시+로그만, 백엔드 등록 엔드포인트 확정 후 PushTokenStore 에서 업로드 연동)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                PushTokenStore.register(this, task.result)
            } else {
                Log.w("FcmToken", "토큰 조회 실패", task.exception)
            }
        }

        // AWS Amplify Auth (Cognito) — Hosted UI 로 Google/Kakao Federated Sign-In + 게스트 Identity Pool.
        // configure 1회만. 실패 시 Log.e 만 남기고 앱은 정상 부팅(콜드 스타트가 인증 실패로 멈추지 않도록).
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(applicationContext)
            Log.i("Amplify", "configured")
        } catch (e: AmplifyException) {
            Log.e("Amplify", "configure failed: ${e.message}", e)
        }

        billingRepository = BillingRepository(this)
        billingRepository.startConnection()

        samsungHealthRepository = SamsungHealthRepository(this)
    }
}
