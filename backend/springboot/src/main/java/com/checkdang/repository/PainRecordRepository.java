package com.checkdang.repository;

import com.checkdang.domain.PainRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PainRecordRepository extends JpaRepository<PainRecord, Long> {

    List<PainRecord> findByUserIdOrderByRecordedAtDesc(String userId);

    List<PainRecord> findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            String userId, LocalDateTime from, LocalDateTime to);

    List<PainRecord> findByUserIdAndBodyPartOrderByRecordedAtDesc(
            String userId, PainRecord.BodyPart bodyPart);
}
