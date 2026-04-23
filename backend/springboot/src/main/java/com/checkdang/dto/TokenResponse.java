package com.checkdang.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponse {
    private String accessToken;
    private String tokenType;
    private UserResponse user;

    public static TokenResponse of(String token, UserResponse user) {
        return TokenResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .user(user)
                .build();
    }
}
