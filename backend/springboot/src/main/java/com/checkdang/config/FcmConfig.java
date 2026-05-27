package com.checkdang.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${firebase.service-account-key:}")
    private String serviceAccountKeyBase64;

    @PostConstruct
    public void initFirebase() {
        if (serviceAccountKeyBase64 == null || serviceAccountKeyBase64.isBlank()) {
            log.warn("FIREBASE_SERVICE_ACCOUNT_KEY 미설정 — FCM 푸시 알림 비활성화");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountKeyBase64);
            InputStream serviceAccount = new ByteArrayInputStream(decoded);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase 초기화 완료");
        } catch (IOException e) {
            log.error("Firebase 초기화 실패: {}", e.getMessage());
        }
    }
}
