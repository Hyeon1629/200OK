package com.checkdang.service;

import com.checkdang.domain.User;
import com.checkdang.dto.GlucoseAlertRequest;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlucoseAlertService {

    private final UserRepository userRepository;
    private final FcmService fcmService;

    public enum GlucoseStatus {
        NORMAL, WARNING, DANGER
    }

    // 프론트엔드 혈당 평가 로직 준수 (위험, 주의 시에만 경고 전송)
    private GlucoseStatus evaluateGlucose(int level, String mealTiming) {
        if (level < 70) {
            return GlucoseStatus.DANGER;
        }

        boolean isFasting = "FASTING".equalsIgnoreCase(mealTiming);
        if (isFasting) {
            if (level < 100) {
                return GlucoseStatus.NORMAL;
            } else if (level < 126) {
                return GlucoseStatus.WARNING;
            } else {
                return GlucoseStatus.DANGER;
            }
        } else {
            // POST_MEAL_2H, AFTER_MEAL, BEFORE_MEAL, BEDTIME 등 기타 식후 기준 일괄 준용
            if (level < 140) {
                return GlucoseStatus.NORMAL;
            } else if (level < 200) {
                return GlucoseStatus.WARNING;
            } else {
                return GlucoseStatus.DANGER;
            }
        }
    }

    // FastAPI가 혈당 저장 시점에 호출.
    // 본인 + 같은 가족그룹의 보호자(CAREGIVER)에게 알림 발송. 알림 비활성 사용자는 스킵.
    public void handleAlert(GlucoseAlertRequest request) {
        Long userId;
        try {
            userId = Long.parseLong(request.getUserId());
        } catch (NumberFormatException e) {
            log.warn("혈당 알림 요청에 유효하지 않은 userId: {}", request.getUserId());
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("혈당 알림 대상 사용자를 찾을 수 없음 — userId: {}", userId);
            return;
        }

        if (request.getLevel() == null) {
            log.warn("혈당 알림 요청에 level 수치가 없습니다.");
            return;
        }

        GlucoseStatus status = evaluateGlucose(request.getLevel(), request.getMealTiming());
        if (status == GlucoseStatus.NORMAL) {
            log.info("혈당 상태 정상 ({} mg/dL, {}) — 알림 발송 건너뜀", request.getLevel(), request.getMealTiming());
            return;
        }

        boolean isLow = request.getLevel() < 70;
        String title;
        String patientBody;
        String statusLabel;

        if (isLow) {
            title = "저혈당 위험";
            patientBody = String.format("%s님, 현재 혈당이 %dmg/dL입니다. 당류 섭취가 필요해요.", user.getName(), request.getLevel());
            statusLabel = "저혈당";
        } else if (status == GlucoseStatus.DANGER) {
            title = "고혈당 위험";
            patientBody = String.format("%s님, 현재 혈당이 %dmg/dL로 매우 높습니다. 조치가 필요해요.", user.getName(), request.getLevel());
            statusLabel = "고혈당 위험";
        } else { // WARNING
            title = "혈당 주의";
            patientBody = String.format("%s님, 현재 혈당이 %dmg/dL입니다. 수치 확인이 필요해요.", user.getName(), request.getLevel());
            statusLabel = "혈당 주의";
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", "GLUCOSE_ALERT");
        data.put("alertType", isLow ? "LOW" : (status == GlucoseStatus.DANGER ? "HIGH" : "WARNING"));
        data.put("level", String.valueOf(request.getLevel()));
        data.put("measuredAt", request.getMeasuredAt() != null ? request.getMeasuredAt() : "");

        notify(user, title, patientBody, data);

        if (user.getFamilyGroupId() != null && !user.getFamilyGroupId().isBlank()) {
            String caregiverBody = String.format("%s님의 혈당이 %dmg/dL(%s)입니다. 확인해주세요.",
                    user.getName(), request.getLevel(), statusLabel);
            userRepository.findByFamilyGroupIdAndFamilyRole(user.getFamilyGroupId(), User.FamilyRole.CAREGIVER)
                    .forEach(caregiver -> notify(caregiver, title, caregiverBody, data));
        }
    }

    private void notify(User user, String title, String body, Map<String, String> data) {
        if (!Boolean.TRUE.equals(user.getNotificationEnabled())) {
            return;
        }
        fcmService.send(user.getFcmToken(), title, body, data);
    }
}
