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

    public enum Role {
        PATIENT, DOCTOR, ADMIN
    }

    public enum Provider {
        LOCAL, GOOGLE, KAKAO
    }
}
