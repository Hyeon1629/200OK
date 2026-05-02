package com.checkdang.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialLoginRequest {
    private String provider;     // GOOGLE or KAKAO
    private String idToken;      // Google
    private String accessToken;  // Kakao
}
