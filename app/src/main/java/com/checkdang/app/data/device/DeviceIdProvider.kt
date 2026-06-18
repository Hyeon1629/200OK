package com.checkdang.app.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * 안정적인 device 식별자. Settings.Secure.ANDROID_ID 기반 (앱 설치 단위 고유).
 *
 * 백엔드 FastAPI 의 heart-rate / step-calorie endpoint 가 `device_id` 필드를 required 로 요구하기 때문에
 * 모든 push 호출이 동일한 값을 사용하도록 단일 진입점을 제공한다.
 */
object DeviceIdProvider {

    @Volatile private var cachedId: String? = null

    @SuppressLint("HardwareIds")
    fun get(context: Context): String {
        cachedId?.let { return it }
        val androidId = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val id = if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
            // 일부 단말이 동일 더미값을 반환 — fallback 으로 random UUID 사용
            "android-${java.util.UUID.randomUUID()}"
        } else {
            "android-$androidId"
        }
        cachedId = id
        return id
    }
}
