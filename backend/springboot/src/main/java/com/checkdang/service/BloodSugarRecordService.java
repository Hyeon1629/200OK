package com.checkdang.service;

import com.checkdang.domain.BloodSugarRecord;
import com.checkdang.domain.User;
import com.checkdang.dto.BloodSugarRecordRequest;
import com.checkdang.dto.BloodSugarRecordResponse;
import com.checkdang.repository.BloodSugarRecordRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class BloodSugarRecordService {

    private final BloodSugarRecordRepository bloodSugarRecordRepository;
    private final UserRepository userRepository;

    public BloodSugarRecordResponse save(String email, BloodSugarRecordRequest request) {
        if (request.getLevel() == null) {
            throw new IllegalArgumentException("혈당 수치는 필수입니다.");
        }
        if (request.getMeasuredAt() == null || request.getMeasuredAt().isBlank()) {
            throw new IllegalArgumentException("측정 시간은 필수입니다.");
        }

        LocalDateTime measuredAt;
        try {
            measuredAt = LocalDateTime.parse(request.getMeasuredAt(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("측정 시간 형식이 올바르지 않습니다. (예: 2025-05-04T09:30:00)");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String date = measuredAt.toLocalDate().toString();
        BloodSugarRecord record = BloodSugarRecord.builder()
                .userDate(user.getId() + "#" + date)
                .timestamp(request.getMeasuredAt())
                .level(request.getLevel())
                .memo(request.getMemo())
                .mealTiming(request.getMealTiming())
                .build();

        bloodSugarRecordRepository.save(record);
        return BloodSugarRecordResponse.from(record);
    }
}
