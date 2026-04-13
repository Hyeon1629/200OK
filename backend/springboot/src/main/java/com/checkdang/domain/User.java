package com.checkdang.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    // Cognito sub (unique identifier per user per user pool)
    @Column(unique = true)
    private String cognitoSub;

    // OAuth provider: GOOGLE, KAKAO, LOCAL
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Provider provider;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void updateName(String name) {
        this.name = name;
    }

    public enum Role {
        PATIENT, DOCTOR, ADMIN
    }

    public enum Provider {
        LOCAL, GOOGLE, KAKAO
    }
}
