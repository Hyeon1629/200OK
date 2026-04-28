package com.checkdang.service;

import com.checkdang.domain.Sleep;
import com.checkdang.domain.SleepStage;
import com.checkdang.domain.User;
import com.checkdang.dto.SleepResponse;
import com.checkdang.dto.SleepSyncRequest;
import com.checkdang.dto.SyncResponse;
import com.checkdang.repository.SleepRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SleepService {

    private final SleepRepository sleepRepository;
    private final UserRepository userRepository;

    @Transactional
    public SyncResponse syncFromSamsungHealth(String userEmail, List<SleepSyncRequest> requests) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        int saved = 0;
        for (SleepSyncRequest req : requests) {
            if (sleepRepository.existsByUserIdAndSleepStart(user.getId(), req.getSleepStart())) {
                continue;
            }

            Sleep sleep = Sleep.builder()
                    .userId(user.getId())
                    .sleepStart(req.getSleepStart())
                    .sleepEnd(req.getSleepEnd())
                    .totalMinutes(req.getTotalMinutes())
                    .efficiency(req.getEfficiency())
                    .dataSource(Sleep.DataSource.SAMSUNG_HEALTH)
                    .build();

            // SleepStage 목록 생성 후 연관관계 설정 (cascade로 함께 저장)
            if (req.getStages() != null) {
                req.getStages().forEach(stageReq -> {
                    SleepStage stage = SleepStage.builder()
                            .sleep(sleep)
                            .stageType(stageReq.getStageType())
                            .startTime(stageReq.getStartTime())
                            .endTime(stageReq.getEndTime())
                            .durationMinutes(stageReq.getDurationMinutes())
                            .build();
                    sleep.getStages().add(stage);
                });
            }

            sleepRepository.save(sleep);
            saved++;
        }

        return SyncResponse.of(saved, requests.size());
    }

    public List<SleepResponse> getSleeps(String userEmail, LocalDateTime from, LocalDateTime to) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        return sleepRepository
                .findWithStagesByUserIdAndRange(user.getId(), from, to)
                .stream()
                .map(SleepResponse::from)
                .toList();
    }
}
