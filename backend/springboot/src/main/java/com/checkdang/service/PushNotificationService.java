package com.checkdang.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PushNotificationService {

    /**
     * 가족 구성원의 통증 기록 시 보호자(OWNER)에게 FCM 푸시 전송.
     *
     * @param fcmToken  수신자 기기의 FCM 토큰
     * @param memberName 통증을 기록한 구성원 이름
     * @param bodyPart   통증 부위 (enum name)
     * @param intensity  통증 강도 1~10
     */
    public void sendPainAlert(String fcmToken, String memberName, String bodyPart, int intensity) {
        if (!isFcmAvailable()) {
            log.debug("FCM 비활성화 상태 — 알림 전송 생략");
            return;
        }

        String body = memberName + "님이 " + translateBodyPart(bodyPart)
                + " 부위에 강도 " + intensity + "의 통증을 기록했습니다.";

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle("가족 통증 알림")
                        .setBody(body)
                        .build())
                .putData("type", "PAIN_ALERT")
                .putData("bodyPart", bodyPart)
                .putData("intensity", String.valueOf(intensity))
                .build();

        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 완료: {}", messageId);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 전송 실패 (token={}): {}", fcmToken, e.getMessage());
        }
    }

    private boolean isFcmAvailable() {
        return !FirebaseApp.getApps().isEmpty();
    }

    private String translateBodyPart(String bodyPart) {
        return switch (bodyPart) {
            case "HEAD" -> "머리";
            case "NECK_FRONT" -> "목(전면)";
            case "NECK_BACK" -> "목(후면)";
            case "CHEST" -> "가슴";
            case "ABDOMEN" -> "복부";
            case "UPPER_BACK" -> "상부 허리";
            case "LOWER_BACK" -> "하부 허리";
            case "LEFT_SHOULDER_FRONT", "LEFT_SHOULDER_BACK" -> "왼쪽 어깨";
            case "RIGHT_SHOULDER_FRONT", "RIGHT_SHOULDER_BACK" -> "오른쪽 어깨";
            case "LEFT_ARM_FRONT" -> "왼팔";
            case "RIGHT_ARM_FRONT" -> "오른팔";
            case "LEFT_HIP_FRONT" -> "왼쪽 골반";
            case "RIGHT_HIP_FRONT" -> "오른쪽 골반";
            case "LEFT_THIGH_FRONT" -> "왼쪽 허벅지";
            case "RIGHT_THIGH_FRONT" -> "오른쪽 허벅지";
            case "LEFT_KNEE" -> "왼쪽 무릎";
            case "RIGHT_KNEE" -> "오른쪽 무릎";
            case "LEFT_SHIN" -> "왼쪽 정강이";
            case "RIGHT_SHIN" -> "오른쪽 정강이";
            default -> bodyPart;
        };
    }
}
