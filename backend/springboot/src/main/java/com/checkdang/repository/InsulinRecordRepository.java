package com.checkdang.repository;

import com.checkdang.domain.InsulinRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InsulinRecordRepository extends JpaRepository<InsulinRecord, Long> {
    List<InsulinRecord> findByUserIdOrderByInjectedAtDesc(String userId);

    // 혈당예측 7피처 — bolus 소싱용 기간 조회 (insulinType RAPID=bolus 로 분류해 사용)
    List<InsulinRecord> findByUserIdAndInjectedAtBetweenOrderByInjectedAtAsc(
            String userId, LocalDateTime from, LocalDateTime to);
}
