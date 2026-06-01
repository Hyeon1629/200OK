package com.checkdang.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.checkdang.app.R
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.ui.main.MainActivity

/**
 * 고/저혈당(위험 범위) 혈당 입력 시 **본인 기기 로컬 알림**.
 *
 * 서버 푸시가 아니라 사용자가 직접 입력한 혈당이 위험 범위일 때 즉시 띄우는 on-device 알림이다.
 * (프리미엄 가족 알림은 푸시 인프라 + 백엔드가 필요한 별개 작업 — 현재 범위 외.)
 *
 * 트리거는 **수동 입력 1건**에 한정한다. 삼성 헬스 대량 동기화에는 호출하지 않아 과거 기록
 * 무더기 알림(스팸)을 막는다.
 */
object GlucoseAlertNotifier {

    private const val CHANNEL_ID = "glucose_alert"

    /** [android.app.Application.onCreate] 에서 1회 호출. 채널 생성은 멱등. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "혈당 위험 알림",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "고혈당·저혈당 등 위험 범위 혈당을 기록하면 알려드려요."
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** API 33+ 만 런타임 권한 필요. 그 이하는 항상 true. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 위험/주의 범위 기록이면 알림. 알림 여부는 [NotificationPrefs] 설정을 따른다.
     *  - 정상: 항상 미발송
     *  - 위험(DANGER): 마스터 ON 이면 발송 (저/고혈당 구분)
     *  - 주의(WARNING): 마스터 ON + "주의 포함" ON 일 때만 발송
     * 권한이 없으면 조용히 무시(알림은 부가 기능 — 본 흐름을 막지 않음).
     */
    fun notifyIfNeeded(context: Context, record: GlucoseRecord) {
        val status = record.status
        if (status == GlucoseStatus.NORMAL) return
        if (!NotificationPrefs.isAlertEnabled(context)) return
        if (status == GlucoseStatus.WARNING && !NotificationPrefs.isIncludeWarning(context)) return
        if (!hasPermission(context)) return

        val isLow = record.value < 70
        val (title, advice) = when {
            status == GlucoseStatus.DANGER && isLow ->
                "저혈당 주의 ⚠️" to "저혈당 범위예요. 빠르게 당분을 섭취하고 상태를 확인하세요."
            status == GlucoseStatus.DANGER ->
                "고혈당 주의 ⚠️" to "고혈당 범위예요. 수분 섭취와 가벼운 활동을 고려하세요."
            else ->
                "혈당 주의 ⚠️" to "주의 범위예요. 식사·활동을 점검하고 추이를 지켜보세요."
        }
        val text = "혈당 ${record.value} mg/dL (${record.timing.label}) · $advice"

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // 권한을 위에서 확인했지만, OS 가 막는 예외 상황에도 흐름이 깨지지 않도록 보호.
        runCatching {
            NotificationManagerCompat.from(context).notify(record.id.hashCode(), notification)
        }
    }
}
