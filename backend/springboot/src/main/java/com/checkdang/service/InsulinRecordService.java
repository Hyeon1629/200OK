package com.checkdang.service;

import com.checkdang.domain.InsulinRecord;
import com.checkdang.domain.User;
import com.checkdang.dto.InsulinRecordRequest;
import com.checkdang.dto.InsulinRecordResponse;
import com.checkdang.repository.InsulinRecordRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class InsulinRecordService {

    private final InsulinRecordRepository insulinRecordRepository;
    private final UserRepository userRepository;

    public InsulinRecordResponse save(String email, InsulinRecordRequest request) {
        if (request.getInsulinType() == null || request.getInsulinType().isBlank()) {
            throw new IllegalArgumentException("인슐린 타입은 필수입니다.");
        }
        if (request.getDosage() == null) {
            throw new IllegalArgumentException("투여량은 필수입니다.");
        }
        if (request.getInjectedAt() == null || request.getInjectedAt().isBlank()) {
            throw new IllegalArgumentException("투여 시간은 필수입니다.");
        }

        LocalDateTime injectedAt;
        try {
            injectedAt = LocalDateTime.parse(request.getInjectedAt(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("투여 시간 형식이 올바르지 않습니다. (예: 2025-05-04T09:30:00)");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        InsulinRecord record = InsulinRecord.builder()
                .insulinType(request.getInsulinType())
                .dosage(request.getDosage())
                .injectedAt(injectedAt)
                .memo(request.getMemo())
                .userId(user.getId())
                .build();

        return InsulinRecordResponse.from(insulinRecordRepository.save(record));
    }
}
