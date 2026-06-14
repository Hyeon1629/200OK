package com.checkdang.service;

import com.checkdang.domain.Diet;
import com.checkdang.domain.User;
import com.checkdang.dto.DietResponse;
import com.checkdang.dto.DietSyncRequest;
import com.checkdang.dto.SyncResponse;
import com.checkdang.repository.DietRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DietService {

    private final DietRepository dietRepository;
    private final UserRepository userRepository;
    private final AiAnalysisService aiAnalysisService;

    @Transactional
    public SyncResponse syncFromSamsungHealth(String userEmail, List<DietSyncRequest> requests) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        int saved = 0;
        for (DietSyncRequest req : requests) {
            if (isDuplicate(String.valueOf(user.getId()), req)) continue;

            dietRepository.save(Diet.builder()
                    .userId(String.valueOf(user.getId()))
                    .sourceId(req.getSourceId())
                    .mealType(req.getMealType())
                    .foodName(req.getFoodName())
                    .calories(req.getCalories())
                    .carbohydrate(req.getCarbohydrate())
                    .protein(req.getProtein())
                    .totalFat(req.getTotalFat())
                    .sugar(req.getSugar())
                    .dietaryFiber(req.getDietaryFiber())
                    .sodium(req.getSodium())
                    .recordedAt(req.getRecordedAt())
                    .dataSource(Diet.DataSource.SAMSUNG_HEALTH)
                    .build());
            saved++;
        }

        // 새 식단이 들어왔으면 이 사용자의 AI 캐시(식단조언·종합리포트)를 무효화한다.
        if (saved > 0) {
            aiAnalysisService.evictUser(String.valueOf(user.getId()));
        }

        return SyncResponse.of(saved, requests.size());
    }

    public List<DietResponse> getDiets(String userEmail, LocalDateTime from, LocalDateTime to) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        return dietRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(String.valueOf(user.getId()), from, to)
                .stream()
                .map(DietResponse::from)
                .toList();
    }

    private boolean isDuplicate(String userId, DietSyncRequest req) {
        if (req.getSourceId() != null) {
            return dietRepository.existsByUserIdAndSourceId(userId, req.getSourceId());
        }
        return dietRepository.existsByUserIdAndRecordedAtAndFoodName(userId, req.getRecordedAt(), req.getFoodName());
    }
}
