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

    @Transactional
    public SyncResponse syncFromSamsungHealth(String userEmail, List<DietSyncRequest> requests) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        int saved = 0;
        for (DietSyncRequest req : requests) {
            if (dietRepository.existsByUserIdAndMealTimeAndFoodName(
                    user.getId(), req.getMealTime(), req.getFoodName())) {
                continue;
            }

            dietRepository.save(Diet.builder()
                    .userId(user.getId())
                    .mealType(req.getMealType())
                    .foodName(req.getFoodName())
                    .calories(req.getCalories())
                    .carbohydrate(req.getCarbohydrate())
                    .protein(req.getProtein())
                    .totalFat(req.getTotalFat())
                    .sugar(req.getSugar())
                    .dietaryFiber(req.getDietaryFiber())
                    .sodium(req.getSodium())
                    .mealTime(req.getMealTime())
                    .dataSource(Diet.DataSource.SAMSUNG_HEALTH)
                    .build());
            saved++;
        }

        return SyncResponse.of(saved, requests.size());
    }

    public List<DietResponse> getDiets(String userEmail, LocalDateTime from, LocalDateTime to) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        return dietRepository
                .findByUserIdAndMealTimeBetweenOrderByMealTimeDesc(user.getId(), from, to)
                .stream()
                .map(DietResponse::from)
                .toList();
    }
}