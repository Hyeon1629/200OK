package com.checkdang.service;

import com.checkdang.domain.User;
import com.checkdang.dto.UserResponse;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public boolean checkEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    // 앱이 FCM 토큰 발급/갱신 시마다 호출 (로그인 직후, 토큰 회전 시 등)
    @Transactional
    public void updateFcmToken(Jwt jwt, String fcmToken) {
        User user = userRepository.findByEmail(resolveEmail(jwt))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }

    // 저혈당/고혈당 등 푸시 알림 수신 여부 설정 (앱 알림 설정 화면에서 호출)
    @Transactional
    public void updateNotificationEnabled(Jwt jwt, boolean notificationEnabled) {
        User user = userRepository.findByEmail(resolveEmail(jwt))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        user.setNotificationEnabled(notificationEnabled);
        userRepository.save(user);
    }

    // Cognito JWT로 인증된 사용자를 RDS에 upsert.
    // 앱이 Cognito 로그인(로컬/소셜) 후 최초 1회 호출 → 이후 모든 API는 Cognito JWT만으로 동작.
    @Transactional
    public UserResponse syncUserFromCognito(Jwt jwt) {
        final String email = resolveEmail(jwt);

        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = email.split("@")[0];
        }

        User.Provider provider = resolveProvider(jwt);
        final String finalName = name;
        final User.Provider finalProvider = provider;

        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setName(finalName);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .name(finalName)
                        .provider(finalProvider)
                        .role(User.Role.PATIENT)
                        .isGuest(false)
                        .accountStatus(User.AccountStatus.ACTIVE)
                        .notificationEnabled(true) // 저혈당/고혈당 알림 등 기본 활성화
                        .build()));

        return UserResponse.from(user);
    }

    // RDS의 LOCAL 회원 전체를 Cognito로 마이그레이션 (1회성 어드민 작업)
    @Transactional(readOnly = true)
    public int migrateLocalUsersToCognito(CognitoService cognitoService) {
        List<User> localUsers = userRepository.findByProvider(User.Provider.LOCAL);
        for (User user : localUsers) {
            try {
                cognitoService.migrateLocalUser(user.getEmail());
            } catch (Exception e) {
                log.error("마이그레이션 실패: {}", user.getEmail(), e);
            }
        }
        return localUsers.size();
    }

    public String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) return email;

        // 카카오: email 미동의/빈 값일 때 cognito:username(kakaooidc_{id}) 기반 합성 이메일로 sub 기반 가입.
        // 기존 카카오 회원과 동일 포맷(kakao_{id}@checkdang.local)이라 중복 없이 매칭됨. (email Optional)
        String cognitoUsername = jwt.getClaimAsString("cognito:username");
        if (cognitoUsername != null && cognitoUsername.toLowerCase().startsWith("kakaooidc_")) {
            String kakaoId = cognitoUsername.substring(cognitoUsername.indexOf('_') + 1);
            return "kakao_" + kakaoId + "@checkdang.local";
        }
        throw new IllegalArgumentException("Cognito 토큰에 email 클레임이 없습니다.");
    }

    // cognito:username prefix로 소셜 provider 판별
    private User.Provider resolveProvider(Jwt jwt) {
        String username = jwt.getClaimAsString("cognito:username");
        if (username != null) {
            String lower = username.toLowerCase();
            if (lower.startsWith("google_")) return User.Provider.GOOGLE;
            if (lower.startsWith("kakaooidc_")) return User.Provider.KAKAO;
        }
        return User.Provider.LOCAL;
    }


}
