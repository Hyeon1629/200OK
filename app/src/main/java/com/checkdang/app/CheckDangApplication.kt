package com.checkdang.app

import android.app.Application
import android.util.Log
import com.checkdang.app.data.billing.BillingRepository
import com.checkdang.app.data.mock.UserStore
import com.checkdang.app.data.samsunghealth.SamsungHealthRepository
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

        billingRepository = BillingRepository(this)
        billingRepository.startConnection()

        samsungHealthRepository = SamsungHealthRepository(this)
    }
}
