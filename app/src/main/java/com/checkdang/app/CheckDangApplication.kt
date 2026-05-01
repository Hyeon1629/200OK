package com.checkdang.app

import android.app.Application
import android.util.Log
import com.checkdang.app.data.mock.UserStore
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.util.Utility

class CheckDangApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, getString(R.string.kakao_app_key))
        Log.d("KakaoKeyHash", Utility.getKeyHash(this))
        UserStore.init(this)
    }
}
