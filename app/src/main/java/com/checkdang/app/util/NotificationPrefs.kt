package com.checkdang.app.util

import android.content.Context

/**
 * 혈당 알림 사용자 설정(SharedPreferences). [GlucoseAlertNotifier] 가 알림 전 참조한다.
 *
 *  - [isAlertEnabled]   : 위험 혈당 로컬 알림 마스터 ON/OFF (기본 ON)
 *  - [isIncludeWarning] : 주의(WARNING) 범위도 알릴지 (기본 OFF — 기본은 위험만)
 */
object NotificationPrefs {

    private const val PREFS = "notification_prefs"
    private const val KEY_ENABLED = "glucose_alert_enabled"
    private const val KEY_INCLUDE_WARNING = "glucose_alert_include_warning"

    fun isAlertEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setAlertEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isIncludeWarning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_WARNING, false)

    fun setIncludeWarning(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_WARNING, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
