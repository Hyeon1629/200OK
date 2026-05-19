package com.checkdang.repository;

import com.checkdang.domain.FamilyInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, Long> {

    Optional<FamilyInvitation> findByInviteCodeAndStatus(String inviteCode, FamilyInvitation.InvitationStatus status);

    Optional<FamilyInvitation> findByFamilyGroupIdAndStatus(String familyGroupId, FamilyInvitation.InvitationStatus status);

    void deleteByExpiresAtBeforeAndStatus(LocalDateTime now, FamilyInvitation.InvitationStatus status);
}
