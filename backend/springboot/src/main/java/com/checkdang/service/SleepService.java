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
            if (sleepRepository.existsByUserIdAndSleepTime(user.getId(), req.getSleepTime())) {
                continue;
            }

            // sleep을 먼저 INSERT해 ID를 확보한 뒤 stages를 연관
            Sleep sleep = sleepRepository.save(Sleep.builder()
                    .userId(user.getId())
                    .sleepTime(req.getSleepTime())
                    .wakeTime(req.getWakeTime())
                    .duration(req.getDuration())
                    .quality(req.getQuality())
                    .dataSource(Sleep.DataSource.SAMSUNG_HEALTH)
                    .build());

            if (req.getStages() != null) {
                req.getStages().forEach(stageReq ->
                        sleep.getStages().add(SleepStage.builder()
                                .sleep(sleep)
                                .stageType(stageReq.getStageType())
                                .startTime(stageReq.getStartTime())
                                .endTime(stageReq.getEndTime())
                                .durationMinutes(stageReq.getDurationMinutes())
                                .build())
                );
                sleepRepository.save(sleep);
            }

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
