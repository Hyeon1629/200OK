package com.checkdang.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${fcm.service-account-key:}")
    private String serviceAccountKey;

    // FIREBASE_SERVICE_ACCOUNT_KEY 미설정 시(로컬 개발 등) null 반환 — FcmService가 발송을 건너뜀
    @Bean
    public FirebaseApp firebaseApp() {
        if (serviceAccountKey == null || serviceAccountKey.isBlank()) {
            log.warn("FIREBASE_SERVICE_ACCOUNT_KEY 미설정 — 푸시 알림 비활성화");
            return null;
        }

        byte[] keyBytes = Base64.getDecoder().decode(serviceAccountKey);
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(keyBytes))
                    .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            return FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
        } catch (Exception e) {
            log.error("Firebase 초기화 실패: {}", e.getMessage());
            return null;
        }
    }
}
