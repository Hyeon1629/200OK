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

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) return email;

        String cognitoUsername = jwt.getClaimAsString("cognito:username");
        if (cognitoUsername != null && cognitoUsername.toLowerCase().startsWith("kakaooidc_")) {
            // TODO: 비즈 앱 전환 후 카카오계정(이메일) 동의항목 활성화 + Cognito email Required 복원 시 제거
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
