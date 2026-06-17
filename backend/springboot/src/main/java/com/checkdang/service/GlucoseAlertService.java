package com.checkdang.service;

import com.checkdang.domain.User;
import com.checkdang.dto.GlucoseAlertRequest;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlucoseAlertService {

    private final UserRepository userRepository;
    private final FcmService fcmService;

    // FastAPI가 혈당 저장 시점에 임계값(저혈당 <70, 고혈당 >180 mg/dL)을 넘으면 호출.
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

        boolean isLow = "LOW".equals(request.getAlertType());
        String title = isLow ? "저혈당 위험" : "고혈당 위험";
        String patientBody = String.format("%s님, 현재 혈당이 %dmg/dL입니다. %s",
                user.getName(), request.getLevel(), isLow ? "당류 섭취가 필요해요." : "수치 확인이 필요해요.");

        Map<String, String> data = Map.of(
                "type", "GLUCOSE_ALERT",
                "alertType", request.getAlertType(),
                "level", String.valueOf(request.getLevel()),
                "measuredAt", request.getMeasuredAt() != null ? request.getMeasuredAt() : ""
        );

        notify(user, title, patientBody, data);

        if (user.getFamilyGroupId() != null && !user.getFamilyGroupId().isBlank()) {
            String caregiverBody = String.format("%s님의 혈당이 %dmg/dL(%s)입니다. 확인해주세요.",
                    user.getName(), request.getLevel(), isLow ? "저혈당" : "고혈당");
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
