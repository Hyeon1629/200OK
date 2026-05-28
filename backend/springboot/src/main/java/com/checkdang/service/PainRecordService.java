package com.checkdang.service;

import com.checkdang.domain.PainRecord;
import com.checkdang.domain.User;
import com.checkdang.dto.PainRecordRequest;
import com.checkdang.dto.PainRecordResponse;
import com.checkdang.repository.PainRecordRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PainRecordService {

    private final PainRecordRepository painRecordRepository;
    private final UserRepository userRepository;

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

        return PainRecordResponse.from(painRecordRepository.save(record));
    }

    public List<PainRecordResponse> getMyRecords(String email) {
        User user = findUser(email);
        return painRecordRepository.findByUserIdOrderByRecordedAtDesc(String.valueOf(user.getId()))
                .stream().map(PainRecordResponse::from).toList();
    }

    public List<PainRecordResponse> getMyRecordsByRange(String email, LocalDateTime from, LocalDateTime to) {
        User user = findUser(email);
        return painRecordRepository.findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        String.valueOf(user.getId()), from, to)
                .stream().map(PainRecordResponse::from).toList();
    }

    public List<PainRecordResponse> getMyRecordsByBodyPart(String email, PainRecord.BodyPart bodyPart) {
        User user = findUser(email);
        return painRecordRepository.findByUserIdAndBodyPartOrderByRecordedAtDesc(
                        String.valueOf(user.getId()), bodyPart)
                .stream().map(PainRecordResponse::from).toList();
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

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}
