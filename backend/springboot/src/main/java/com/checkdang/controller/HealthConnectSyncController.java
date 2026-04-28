package com.checkdang.controller;

import com.checkdang.dto.DietResponse;
import com.checkdang.dto.DietSyncRequest;
import com.checkdang.dto.SyncResponse;
import com.checkdang.service.DietService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/samsung-health")
@RequiredArgsConstructor
public class HealthConnectSyncController {

    private final DietService dietService;

    @PostMapping("/diets")
    public ResponseEntity<SyncResponse> syncDiets(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid List<DietSyncRequest> requests) {
        // principal.getUsername()은 이메일 반환 (UserService.loadUserByUsername 참고)
        String userEmail = principal.getUsername();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dietService.syncFromSamsungHealth(userEmail, requests));
    }

    @GetMapping("/diets")
    public ResponseEntity<List<DietResponse>> getDiets(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        String userEmail = principal.getUsername();
        return ResponseEntity.ok(dietService.getDiets(userEmail, from, to));
    }
}
