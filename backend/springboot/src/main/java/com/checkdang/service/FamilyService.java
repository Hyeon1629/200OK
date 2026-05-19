package com.checkdang.service;

import com.checkdang.domain.FamilyInvitation;
import com.checkdang.domain.PainRecord;
import com.checkdang.domain.User;
import com.checkdang.dto.DietResponse;
import com.checkdang.dto.FamilyMemberResponse;
import com.checkdang.dto.InsulinRecordResponse;
import com.checkdang.dto.PainRecordResponse;
import com.checkdang.dto.SleepResponse;
import com.checkdang.repository.DietRepository;
import com.checkdang.repository.FamilyInvitationRepository;
import com.checkdang.repository.InsulinRecordRepository;
import com.checkdang.repository.PainRecordRepository;
import com.checkdang.repository.SleepRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamilyService {

    private final UserRepository userRepository;
    private final FamilyInvitationRepository familyInvitationRepository;
    private final InsulinRecordRepository insulinRecordRepository;
    private final DietRepository dietRepository;
    private final SleepRepository sleepRepository;
    private final PainRecordRepository painRecordRepository;

    private static final int MAX_FAMILY_MEMBERS = 5;
    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public String createGroup(String email) {
        User owner = findUser(email);
        if (!Boolean.TRUE.equals(owner.getIsPremium())) {
            throw new IllegalArgumentException("프리미엄 구독자만 가족 그룹을 생성할 수 있습니다.");
        }
        if (owner.getFamilyGroupId() != null) {
            throw new IllegalArgumentException("이미 가족 그룹에 속해 있습니다.");
        }

        String groupId = UUID.randomUUID().toString();
        owner.setFamilyGroupId(groupId);
        owner.setFamilyRole(User.FamilyRole.OWNER);
        userRepository.save(owner);

        return groupId;
    }

    @Transactional
    public String generateInviteCode(String email) {
        User owner = findUser(email);
        validateOwner(owner);

        List<User> members = userRepository.findByFamilyGroupId(owner.getFamilyGroupId());
        if (members.size() >= MAX_FAMILY_MEMBERS) {
            throw new IllegalArgumentException("가족 구성원은 최대 " + MAX_FAMILY_MEMBERS + "명까지 추가할 수 있습니다.");
        }

        // 기존 활성 코드 만료 처리 후 새 코드 발급
        familyInvitationRepository.findByFamilyGroupIdAndStatus(
                owner.getFamilyGroupId(), FamilyInvitation.InvitationStatus.ACTIVE)
                .ifPresent(inv -> {
                    throw new IllegalArgumentException(
                            "이미 유효한 초대 코드가 있습니다: " + inv.getInviteCode() + " (7일간 유효)");
                });

        String code = generateCode();
        familyInvitationRepository.save(FamilyInvitation.builder()
                .inviteCode(code)
                .familyGroupId(owner.getFamilyGroupId())
                .ownerEmail(email)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .status(FamilyInvitation.InvitationStatus.ACTIVE)
                .build());

        return code;
    }

    @Transactional
    public void joinByCode(String email, String inviteCode) {
        User newMember = findUser(email);
        if (newMember.getFamilyGroupId() != null) {
            throw new IllegalArgumentException("이미 가족 그룹에 속해 있습니다.");
        }

        FamilyInvitation invitation = familyInvitationRepository
                .findByInviteCodeAndStatus(inviteCode, FamilyInvitation.InvitationStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("만료된 초대 코드입니다.");
        }

        List<User> members = userRepository.findByFamilyGroupId(invitation.getFamilyGroupId());
        if (members.size() >= MAX_FAMILY_MEMBERS) {
            throw new IllegalArgumentException("가족 구성원이 가득 찼습니다.");
        }

        newMember.setFamilyGroupId(invitation.getFamilyGroupId());
        newMember.setFamilyRole(User.FamilyRole.CAREGIVER);
        userRepository.save(newMember);
    }

    public List<FamilyMemberResponse> getMembers(String email) {
        User user = findUser(email);
        validateFamilyMember(user);

        return userRepository.findByFamilyGroupId(user.getFamilyGroupId()).stream()
                .map(FamilyMemberResponse::from)
                .toList();
    }

    @Transactional
    public void removeMember(String ownerEmail, Long targetUserId) {
        User owner = findUser(ownerEmail);
        validateOwner(owner);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (!owner.getFamilyGroupId().equals(target.getFamilyGroupId())) {
            throw new IllegalArgumentException("같은 가족 그룹의 구성원이 아닙니다.");
        }
        if (owner.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("자신은 제거할 수 없습니다. 그룹 해산을 이용하세요.");
        }

        target.setFamilyGroupId(null);
        target.setFamilyRole(null);
        userRepository.save(target);
    }

    @Transactional
    public void leaveGroup(String email) {
        User user = findUser(email);
        validateFamilyMember(user);

        if (user.getFamilyRole() == User.FamilyRole.OWNER) {
            // OWNER 탈퇴 시 그룹 전체 해산
            List<User> members = userRepository.findByFamilyGroupId(user.getFamilyGroupId());
            members.forEach(m -> {
                m.setFamilyGroupId(null);
                m.setFamilyRole(null);
            });
            userRepository.saveAll(members);
        } else {
            user.setFamilyGroupId(null);
            user.setFamilyRole(null);
            userRepository.save(user);
        }
    }

    public List<InsulinRecordResponse> getMemberInsulinRecords(String requestorEmail, Long targetUserId) {
        validateFamilyAccess(requestorEmail, targetUserId);
        return insulinRecordRepository.findByUserIdOrderByInjectedAtDesc(String.valueOf(targetUserId))
                .stream()
                .map(InsulinRecordResponse::from)
                .toList();
    }

    public List<DietResponse> getMemberDiets(String requestorEmail, Long targetUserId,
                                             LocalDateTime from, LocalDateTime to) {
        validateFamilyAccess(requestorEmail, targetUserId);
        return dietRepository.findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        String.valueOf(targetUserId), from, to)
                .stream()
                .map(DietResponse::from)
                .toList();
    }

    public List<SleepResponse> getMemberSleeps(String requestorEmail, Long targetUserId,
                                               LocalDateTime from, LocalDateTime to) {
        validateFamilyAccess(requestorEmail, targetUserId);
        return sleepRepository.findWithStagesByUserIdAndRange(String.valueOf(targetUserId), from, to)
                .stream()
                .map(SleepResponse::from)
                .toList();
    }

    public List<PainRecordResponse> getMemberPainRecords(String requestorEmail, Long targetUserId) {
        validateFamilyAccess(requestorEmail, targetUserId);
        return painRecordRepository.findByUserIdOrderByRecordedAtDesc(String.valueOf(targetUserId))
                .stream()
                .map(PainRecordResponse::from)
                .toList();
    }

    public List<PainRecordResponse> getMemberPainRecordsByRange(String requestorEmail, Long targetUserId,
                                                                LocalDateTime from, LocalDateTime to) {
        validateFamilyAccess(requestorEmail, targetUserId);
        return painRecordRepository.findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        String.valueOf(targetUserId), from, to)
                .stream()
                .map(PainRecordResponse::from)
                .toList();
    }

    private void validateFamilyAccess(String requestorEmail, Long targetUserId) {
        User requestor = findUser(requestorEmail);
        validateFamilyMember(requestor);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (!requestor.getFamilyGroupId().equals(target.getFamilyGroupId())) {
            throw new IllegalArgumentException("같은 가족 그룹의 구성원만 조회할 수 있습니다.");
        }
    }

    private void validateOwner(User user) {
        if (user.getFamilyGroupId() == null || user.getFamilyRole() != User.FamilyRole.OWNER) {
            throw new IllegalArgumentException("가족 그룹 OWNER만 이 작업을 수행할 수 있습니다.");
        }
    }

    private void validateFamilyMember(User user) {
        if (user.getFamilyGroupId() == null) {
            throw new IllegalArgumentException("가족 그룹에 속해 있지 않습니다.");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(INVITE_CHARS.charAt(RANDOM.nextInt(INVITE_CHARS.length())));
        }
        return sb.toString();
    }
}
