package com.checkdang.service;

import com.checkdang.domain.PainRecord;
import com.checkdang.domain.User;
import com.checkdang.dto.PainCorrelationDto;
import com.checkdang.dto.PainRecordAnalysisResponse;
import com.checkdang.dto.PainRecordRequest;
import com.checkdang.dto.PainRecordResponse;
import com.checkdang.repository.PainRecordRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PainRecordService {

    private static final Set<PainRecord.BodyPart> EXERCISE_RELATED_PARTS = Set.of(
            PainRecord.BodyPart.LOWER_BACK,
            PainRecord.BodyPart.LEFT_KNEE,
            PainRecord.BodyPart.RIGHT_KNEE,
            PainRecord.BodyPart.LEFT_THIGH_FRONT,
            PainRecord.BodyPart.RIGHT_THIGH_FRONT
    );

    private final PainRecordRepository painRecordRepository;
    private final UserRepository userRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public PainRecordResponse save(String email, PainRecordRequest request) {
        validateRequest(request);
        User user = findUser(email);

        PainRecord record = PainRecord.builder()
                .userId(String.valueOf(user.getId()))
                .intensity(request.getIntensity())
                .bodyPart(request.getBodyPart())
                .painTypes(request.getPainTypes())
                .recordedAt(LocalDateTime.now())
                .build();

        PainRecord saved = painRecordRepository.save(record);
        sendFamilyPainAlert(user, saved);
        return PainRecordResponse.from(saved);
    }

    @Transactional
    public PainRecordAnalysisResponse saveAndAnalyze(String email, PainRecordRequest request) {
        validateRequest(request);
        User user = findUser(email);

        PainRecord record = PainRecord.builder()
                .userId(String.valueOf(user.getId()))
                .intensity(request.getIntensity())
                .bodyPart(request.getBodyPart())
                .painTypes(request.getPainTypes())
                .recordedAt(LocalDateTime.now())
                .build();

        PainRecord saved = painRecordRepository.save(record);
        sendFamilyPainAlert(user, saved);

        // FastAPI Gemini 분석 호출 — 실패해도 저장된 기록은 유지
        try {
            Map<String, Object> payload = Map.of(
                    "record_id", saved.getId(),
                    "body_part", saved.getBodyPart().name(),
                    "pain_types", saved.getPainTypes().stream().map(Enum::name).toList(),
                    "intensity", saved.getIntensity()
            );
            AiAnalysisClient.PainAiResult aiResult = aiAnalysisClient.analyzePain(payload);
            saved.setAiCause(aiResult.aiCause());
            saved.setAiFirstAid(aiResult.aiFirstAid());
        } catch (Exception e) {
            log.warn("FastAPI pain analysis failed for record {}: {}", saved.getId(), e.getMessage());
        }

        return buildAnalysis(saved);
    }

    public List<PainRecordResponse> getMyRecords(String email) {
        User user = findUser(email);
        return painRecordRepository.findByUserIdOrderByRecordedAtDesc(String.valueOf(user.getId()))
                .stream()
                .map(PainRecordResponse::from)
                .toList();
    }

    public List<PainRecordResponse> getMyRecordsByRange(String email, LocalDateTime from, LocalDateTime to) {
        User user = findUser(email);
        return painRecordRepository.findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        String.valueOf(user.getId()), from, to)
                .stream()
                .map(PainRecordResponse::from)
                .toList();
    }

    public List<PainRecordResponse> getMyRecordsByBodyPart(String email, PainRecord.BodyPart bodyPart) {
        User user = findUser(email);
        return painRecordRepository.findByUserIdAndBodyPartOrderByRecordedAtDesc(
                        String.valueOf(user.getId()), bodyPart)
                .stream()
                .map(PainRecordResponse::from)
                .toList();
    }

    public PainRecordResponse getById(String email, Long recordId) {
        User user = findUser(email);
        PainRecord record = painRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("통증 기록을 찾을 수 없습니다."));

        if (!record.getUserId().equals(String.valueOf(user.getId()))) {
            throw new IllegalArgumentException("본인의 통증 기록만 조회할 수 있습니다.");
        }
        return PainRecordResponse.from(record);
    }

    @Transactional
    public void delete(String email, Long recordId) {
        User user = findUser(email);
        PainRecord record = painRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("통증 기록을 찾을 수 없습니다."));

        if (!record.getUserId().equals(String.valueOf(user.getId()))) {
            throw new IllegalArgumentException("본인의 통증 기록만 삭제할 수 있습니다.");
        }
        painRecordRepository.delete(record);
    }

    private PainRecordAnalysisResponse buildAnalysis(PainRecord record) {
        List<PainCorrelationDto> correlations = new ArrayList<>();

        // 혈당 변동성: 항상 포함, 강도 4 이상이면 HIGH
        correlations.add(PainCorrelationDto.builder()
                .factor("혈당 변동성")
                .level(record.getIntensity() >= 4 ? "HIGH" : "MEDIUM")
                .description("혈당 수치의 급격한 변화가 통증에 영향을 줄 수 있습니다.")
                .build());

        // 수면 부족: 항상 포함, MEDIUM
        correlations.add(PainCorrelationDto.builder()
                .factor("수면 부족")
                .level("MEDIUM")
                .description("수면 부족은 통증 민감도를 높일 수 있습니다.")
                .build());

        // 운동 강도: 특정 부위일 때만
        if (EXERCISE_RELATED_PARTS.contains(record.getBodyPart())) {
            correlations.add(PainCorrelationDto.builder()
                    .factor("운동 강도")
                    .level("LOW")
                    .description("해당 부위는 과도한 운동 후 통증이 발생하기 쉽습니다.")
                    .build());
        }

        // 신경 민감도: NUMBNESS 또는 BURNING 포함 시
        List<PainRecord.PainType> painTypes = record.getPainTypes();
        if (painTypes != null && (painTypes.contains(PainRecord.PainType.NUMBNESS)
                || painTypes.contains(PainRecord.PainType.BURNING))) {
            correlations.add(PainCorrelationDto.builder()
                    .factor("신경 민감도")
                    .level("HIGH")
                    .description("저림 또는 화끈거림 증상은 신경 관련 문제일 수 있습니다.")
                    .build());
        }

        String summary = String.format("%s 부위에 강도 %d의 통증이 기록되었습니다.",
                record.getBodyPart().name(), record.getIntensity());

        String recommendation;
        if (record.getIntensity() >= 7) {
            recommendation = "통증 강도가 높습니다. 의료진과 상담을 권장드립니다.";
        } else if (record.getIntensity() >= 4) {
            recommendation = "통증이 지속된다면 전문가 상담을 고려해보세요.";
        } else {
            recommendation = "지속적인 모니터링을 권장드립니다.";
        }

        return PainRecordAnalysisResponse.builder()
                .record(PainRecordResponse.from(record))
                .summary(summary)
                .correlations(correlations)
                .recommendation(recommendation)
                .build();
    }

    private void validateRequest(PainRecordRequest request) {
        if (request.getIntensity() == null || request.getIntensity() < 1 || request.getIntensity() > 10) {
            throw new IllegalArgumentException("통증 강도는 1~10 사이의 값이어야 합니다.");
        }
        if (request.getBodyPart() == null) {
            throw new IllegalArgumentException("통증 부위를 선택해주세요.");
        }
        if (request.getPainTypes() == null || request.getPainTypes().isEmpty()) {
            throw new IllegalArgumentException("통증 종류를 하나 이상 선택해주세요.");
        }
    }

    /**
     * 통증 기록자가 가족 그룹에 속해 있으면 같은 그룹의 다른 구성원에게 FCM 푸시 전송.
     * 알림 수신 설정(notificationEnabled)이 꺼진 구성원은 제외.
     */
    private void sendFamilyPainAlert(User recorder, PainRecord record) {
        if (recorder.getFamilyGroupId() == null) return;

        userRepository.findByFamilyGroupId(recorder.getFamilyGroupId()).stream()
                .filter(m -> !m.getId().equals(recorder.getId()))
                .filter(m -> !Boolean.FALSE.equals(m.getNotificationEnabled()))
                .filter(m -> m.getFcmToken() != null && !m.getFcmToken().isBlank())
                .forEach(m -> pushNotificationService.sendPainAlert(
                        m.getFcmToken(),
                        recorder.getName(),
                        record.getBodyPart().name(),
                        record.getIntensity()
                ));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}
