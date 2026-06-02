package com.checkdang.app.push

import android.Manifest
import android.annotation.SuppressLint
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
import com.checkdang.app.ui.main.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 서비스. 백엔드(Firebase 프로젝트 checkdang-65238)가 보내는 푸시 알림을 처리한다.
 *  - [onNewToken]: 등록 토큰 발급/갱신 → [PushTokenStore] 가 캐시 + (TODO) 백엔드 등록
 *  - [onMessageReceived]: 포그라운드 수신(또는 data 메시지) 시 알림 표시
 *
 * 앱이 백그라운드일 때의 notification 메시지는 시스템이 직접 트레이에 표시한다
 * (매니페스트 default_notification_channel_id / icon 메타데이터 사용 → onMessageReceived 미호출).
 */
class CheckDangMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushTokenStore.register(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // notification 메시지(title/body) 우선, 없으면 data 페이로드로 폴백. 둘 다 없으면 무시.
        val title = message.notification?.title ?: message.data["title"] ?: "체크당"
        val body = message.notification?.body ?: message.data["body"] ?: return
        show(title, body)
    }

    // 권한은 hasPermission() 으로 명시 확인 후 notify 하므로 안전(+runCatching 이중 보호).
    @SuppressLint("MissingPermission")
    private fun show(title: String, body: String) {
        if (!hasPermission(this)) return

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "push_default"

        /** [android.app.Application.onCreate] 에서 1회 호출. 채널 생성은 멱등. */
        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "푸시 알림",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "체크당 서비스 알림(공지·리마인더 등)을 받아요."
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        /** API 33+ 만 런타임 권한 필요. 그 이하는 항상 true. */
        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
