package com.checkdang.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    private String providerId;

    @Column(name = "is_guest")
    private Boolean isGuest;

    private Boolean termsAgreed;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String birthDate;

    private Integer height;

    private Integer weight;

    @Enumerated(EnumType.STRING)
    private DiabetesType diabetesType;

    @Column(name = "is_premium")
    private Boolean isPremium;

    // Instant 타입: 결제 서비스에서 Instant로 계산하므로 그대로 유지
    // Hibernate가 DATETIME 컬럼에 자동 매핑
    private Instant premiumExpiresAt;

    private String familyGroupId;

    @Enumerated(EnumType.STRING)
    private FamilyRole familyRole;

    private Integer targetBloodSugar;

    @Column(name = "samsung_health_connected")
    private Boolean samsungHealthConnected;

    @Column(name = "notification_enabled")
    private Boolean notificationEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

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
