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

    // 통증 AI 분석용 — 유저 + 같은 부위 + 기간(최근 1주일) 조합 (재발·악화 추세 분석)
    List<PainRecord> findByUserIdAndBodyPartAndRecordedAtBetweenOrderByRecordedAtDesc(
            String userId, PainRecord.BodyPart bodyPart, LocalDateTime from, LocalDateTime to);
}
