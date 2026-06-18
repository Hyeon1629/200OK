package com.checkdang.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${fcm.service-account-file:}")
    private String serviceAccountFile;

    @Value("${fcm.service-account-key:}")
    private String serviceAccountKey;

    @Bean
    public FirebaseApp firebaseApp() {
        InputStream serviceAccountStream = null;

        try {
            // 1. 설정파일 경로가 있는 경우 해당 경로(클래스패스 또는 로컬 파일)에서 JSON 파일 로드 시도
            if (serviceAccountFile != null && !serviceAccountFile.isBlank()) {
                if (serviceAccountFile.startsWith("classpath:")) {
                    String resourcePath = serviceAccountFile.substring(10);
                    serviceAccountStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                } else {
                    File file = new File(serviceAccountFile);
                    if (file.exists()) {
                        serviceAccountStream = new FileInputStream(file);
                    }
                }
            }

            // 2. 자동 감지: 파일명 "firebase-service-account.json"이 클래스패스나 현재 실행 경로에 존재하는지 체크
            if (serviceAccountStream == null) {
                File defaultFile = new File("firebase-service-account.json");
                if (defaultFile.exists()) {
                    serviceAccountStream = new FileInputStream(defaultFile);
                    log.info("자동 감지된 파일에서 Firebase 설정을 로드합니다: ./firebase-service-account.json");
                } else {
                    InputStream classpathStream = getClass().getClassLoader().getResourceAsStream("firebase-service-account.json");
                    if (classpathStream != null) {
                        serviceAccountStream = classpathStream;
                        log.info("자동 감지된 클래스패스 파일에서 Firebase 설정을 로드합니다: classpath:firebase-service-account.json");
                    }
                }
            }

            // 3. 파일이 없는 경우, 기존 serviceAccountKey 환경변수 문자열(JSON 원본 혹은 Base64)에서 로드 시도
            if (serviceAccountStream == null && serviceAccountKey != null && !serviceAccountKey.isBlank()) {
                byte[] keyBytes;
                String trimmedKey = serviceAccountKey.trim();
                if (trimmedKey.startsWith("{")) {
                    keyBytes = trimmedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    keyBytes = Base64.getDecoder().decode(trimmedKey);
                }
                serviceAccountStream = new ByteArrayInputStream(keyBytes);
            }

            // 4. 모든 방법 실패 시 알림 비활성화 후 정상 부팅
            if (serviceAccountStream == null) {
                log.warn("Firebase 서비스 계정 설정(JSON 파일 혹은 환경변수 키)이 지정되지 않았습니다 — 푸시 알림 비활성화");
                return null;
            }

            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(serviceAccountStream)
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
        } finally {
            if (serviceAccountStream != null) {
                try {
                    serviceAccountStream.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
