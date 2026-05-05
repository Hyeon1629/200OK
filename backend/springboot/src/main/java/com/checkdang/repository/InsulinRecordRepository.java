package com.checkdang.repository;

import com.checkdang.domain.InsulinRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsulinRecordRepository extends JpaRepository<InsulinRecord, Long> {
    List<InsulinRecord> findByUserIdOrderByInjectedAtDesc(String userId);
}
