package com.checkdang.controller;

import com.checkdang.dto.DietResponse;
import com.checkdang.dto.DietSyncRequest;
import com.checkdang.dto.ExerciseResponse;
import com.checkdang.dto.ExerciseSyncRequest;
import com.checkdang.dto.SleepResponse;
import com.checkdang.dto.SleepSyncRequest;
import com.checkdang.dto.SyncResponse;
import com.checkdang.service.DietService;
import com.checkdang.service.ExerciseService;
import com.checkdang.service.SleepService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/samsung-health")
@RequiredArgsConstructor
public class HealthConnectSyncController {

    private final DietService dietService;
    private final SleepService sleepService;
    private final ExerciseService exerciseService;

    @PostMapping("/diets")
    public ResponseEntity<SyncResponse> syncDiets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid List<DietSyncRequest> requests) {
        String userEmail = jwt.getClaimAsString("email");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dietService.syncFromSamsungHealth(userEmail, requests));
    }

    @GetMapping("/diets")
    public ResponseEntity<List<DietResponse>> getDiets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        String userEmail = jwt.getClaimAsString("email");
        return ResponseEntity.ok(dietService.getDiets(userEmail, from, to));
    }

    @PostMapping("/sleeps")
    public ResponseEntity<SyncResponse> syncSleeps(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid List<SleepSyncRequest> requests) {
        String userEmail = jwt.getClaimAsString("email");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sleepService.syncFromSamsungHealth(userEmail, requests));
    }

    @GetMapping("/sleeps")
    public ResponseEntity<List<SleepResponse>> getSleeps(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        String userEmail = jwt.getClaimAsString("email");
        return ResponseEntity.ok(sleepService.getSleeps(userEmail, from, to));
    }

    @PostMapping("/exercises")
    public ResponseEntity<SyncResponse> syncExercises(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid List<ExerciseSyncRequest> requests) {
        String userEmail = jwt.getClaimAsString("email");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(exerciseService.syncFromSamsungHealth(userEmail, requests));
    }

    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseResponse>> getExercises(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        String userEmail = jwt.getClaimAsString("email");
        return ResponseEntity.ok(exerciseService.getExercises(userEmail, from, to));
    }
}
