package com.checkdang.service;

import com.checkdang.domain.RefreshToken;
import com.checkdang.domain.User;
import com.checkdang.dto.SocialLoginRequest;
import com.checkdang.dto.TokenResponse;
import com.checkdang.dto.UserResponse;
import com.checkdang.repository.RefreshTokenRepository;
import com.checkdang.repository.UserRepository;
import com.checkdang.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    public TokenResponse socialLogin(SocialLoginRequest request) {
        String provider = request.getProvider().toUpperCase();

        String email;
        String name;
        String providerId;

        if ("GOOGLE".equals(provider)) {
            Map<String, Object> info = verifyGoogleToken(request.getIdToken());
            email      = (String) info.get("email");
            name       = (String) info.get("name");
            providerId = (String) info.get("sub");
        } else if ("KAKAO".equals(provider)) {
            Map<String, Object> info = verifyKakaoToken(request.getAccessToken());
            email      = (String) info.get("email");
            name       = (String) info.get("nickname");
            providerId = String.valueOf(info.get("id"));
        } else {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다.");
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createSocialUser(email, name, providerId,
                        User.Provider.valueOf(provider)));

        String accessToken  = jwtTokenProvider.generateToken(user.getEmail());
        String refreshToken = createRefreshToken(user.getId());

        return TokenResponse.of(accessToken, refreshToken, UserResponse.from(user));
    }

    private Map<String, Object> verifyGoogleToken(String idToken) {
        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(url, HttpMethod.GET, null, MAP_TYPE);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new IllegalArgumentException("유효하지 않은 Google 토큰입니다.");
            }
            return response.getBody();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Google 토큰 검증에 실패했습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyKakaoToken(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "https://kapi.kakao.com/v2/user/me",
                    HttpMethod.GET, entity, MAP_TYPE);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new IllegalArgumentException("유효하지 않은 Kakao 토큰입니다.");
            }

            Map<String, Object> body        = response.getBody();
            Map<String, Object> kakaoAccount = (Map<String, Object>) body.get("kakao_account");
            Map<String, Object> profile      = (Map<String, Object>) kakaoAccount.get("profile");

            String email = (String) kakaoAccount.get("email");
            if (email == null) {
                email = "kakao_" + body.get("id") + "@checkdang.com";
            }

            return Map.of(
                    "id",       body.get("id"),
                    "email",    email,
                    "nickname", profile.get("nickname")
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Kakao 토큰 검증에 실패했습니다.");
        }
    }

    private User createSocialUser(String email, String name, String providerId,
                                  User.Provider provider) {
        return userRepository.save(User.builder()
                .email(email)
                .name(name)
                .provider(provider)
                .providerId(providerId)
                .role(User.Role.PATIENT)
                .isGuest(false)
                .accountStatus(User.AccountStatus.ACTIVE)
                .build());
    }

    private String createRefreshToken(String userId) {
        String token   = UUID.randomUUID().toString();
        long expiresAt = Instant.now().getEpochSecond() + (refreshExpiration / 1000);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(token)
                .userId(userId)
                .expiresAt(expiresAt)
                .createdAt(Instant.now().toString())
                .build());

        return token;
    }
}
