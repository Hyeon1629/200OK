package com.checkdang.controller;

import com.checkdang.dto.ApiResponse;
import com.checkdang.dto.DietResponse;
import com.checkdang.dto.FamilyJoinRequest;
import com.checkdang.dto.FamilyMemberResponse;
import com.checkdang.dto.InsulinRecordResponse;
import com.checkdang.dto.PainRecordResponse;
import com.checkdang.dto.SleepResponse;
import com.checkdang.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    /** 가족 그룹 생성 (프리미엄 구독자만) */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Map<String, String>>> createGroup(
            @AuthenticationPrincipal UserDetails userDetails) {
        String groupId = familyService.createGroup(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("familyGroupId", groupId)));
    }

    /** 초대 코드 발급 (OWNER만, 7일 유효) */
    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<Map<String, String>>> invite(
            @AuthenticationPrincipal UserDetails userDetails) {
        String code = familyService.generateInviteCode(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("inviteCode", code)));
    }

    /** 초대 코드로 가족 그룹 가입 */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<Void>> join(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody FamilyJoinRequest request) {
        familyService.joinByCode(userDetails.getUsername(), request.getInviteCode());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 가족 구성원 목록 조회 */
    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<FamilyMemberResponse>>> getMembers(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(familyService.getMembers(userDetails.getUsername())));
    }

    /** 가족 구성원 제거 (OWNER만) */
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long memberId) {
        familyService.removeMember(userDetails.getUsername(), memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 가족 그룹 탈퇴 (OWNER 탈퇴 시 그룹 해산) */
    @DeleteMapping("/leave")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(
            @AuthenticationPrincipal UserDetails userDetails) {
        familyService.leaveGroup(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 가족 구성원 인슐린 기록 조회 */
    @GetMapping("/members/{targetUserId}/insulin-records")
    public ResponseEntity<ApiResponse<List<InsulinRecordResponse>>> getMemberInsulinRecords(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long targetUserId) {
        return ResponseEntity.ok(ApiResponse.ok(
                familyService.getMemberInsulinRecords(userDetails.getUsername(), targetUserId)));
    }

    /** 가족 구성원 식단 기록 조회 */
    @GetMapping("/members/{targetUserId}/diets")
    public ResponseEntity<ApiResponse<List<DietResponse>>> getMemberDiets(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long targetUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.ok(
                familyService.getMemberDiets(userDetails.getUsername(), targetUserId, from, to)));
    }

    /** 가족 구성원 수면 기록 조회 */
    @GetMapping("/members/{targetUserId}/sleeps")
    public ResponseEntity<ApiResponse<List<SleepResponse>>> getMemberSleeps(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long targetUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.ok(
                familyService.getMemberSleeps(userDetails.getUsername(), targetUserId, from, to)));
    }

    /** 가족 구성원 통증 기록 조회 (전체) */
    @GetMapping("/members/{targetUserId}/pain-records")
    public ResponseEntity<ApiResponse<List<PainRecordResponse>>> getMemberPainRecords(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long targetUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<PainRecordResponse> result = (from != null && to != null)
                ? familyService.getMemberPainRecordsByRange(userDetails.getUsername(), targetUserId, from, to)
                : familyService.getMemberPainRecords(userDetails.getUsername(), targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
