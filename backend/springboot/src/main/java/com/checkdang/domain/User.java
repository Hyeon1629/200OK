package com.checkdang.domain;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String id;
    private String email;
    private String password;
    private String name;
    private Role role;
    private Provider provider;
    private String providerId;
    private Instant createdAt;

    // A 담당 필드
    private Boolean isGuest;
    private Boolean termsAgreed;
    private Gender gender;
    private String birthDate;
    private Integer height;
    private Integer weight;
    private DiabetesType diabetesType;

    // B 담당 필드
    private Boolean isPremium;
    private Instant premiumExpiresAt;
    private String familyGroupId;
    private FamilyRole familyRole;
    private Integer targetBloodSugar;
    private Boolean samsungHealthConnected;
    private Boolean notificationEnabled;
    private AccountStatus accountStatus;

    public enum Role {
        PATIENT, CAREGIVER, ADMIN
    }

    public enum Provider {
        LOCAL, GOOGLE, KAKAO
    }

    public enum DiabetesType {
        TYPE1, TYPE2, PRE, NONE
    }

    public enum Gender {
        MALE, FEMALE
    }

    public enum FamilyRole {
        OWNER, CAREGIVER
    }

    public enum AccountStatus {
        ACTIVE, SUSPENDED, DELETED
    }
}
