package com.checkdang.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class FcmService {

    private final FirebaseApp firebaseApp;

    public FcmService(Optional<FirebaseApp> firebaseApp) {
        this.firebaseApp = firebaseApp.orElse(null);
    }

    // firebaseApp이 null이면(FCM_SERVICE_ACCOUNT_KEY 미설정) 발송을 건너뛰고 로그만 남긴다.
    public void send(String fcmToken, String title, String body, Map<String, String> data) {
        if (firebaseApp == null || fcmToken == null || fcmToken.isBlank()) {
            log.warn("FCM 발송 스킵 — firebaseApp 또는 fcmToken 없음");
            return;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                // Android 기기에서 백그라운드 수신 확률 극대화 (우선순위 높음 설정)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .build();

        try {
            FirebaseMessaging.getInstance(firebaseApp).send(message);
        } catch (FirebaseMessagingException e) {
            log.error("FCM 발송 실패 — token: {}, error: {}", fcmToken, e.getMessage());
        }
    }
}
