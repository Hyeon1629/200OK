package com.checkdang.service;

import com.checkdang.domain.User;
import com.checkdang.dto.LoginRequest;
import com.checkdang.dto.SignupRequest;
import com.checkdang.dto.UserResponse;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Cognito JWT로 로그인 시 DB에 유저가 없으면 자동 생성 (소셜 첫 로그인)
     * Cognito sub를 기준으로 유저를 식별합니다.
     */
    @Transactional
    public UserResponse syncCognitoUser(Jwt jwt) {
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        if (name == null) {
            name = email;
        }

        User.Provider provider = resolveProvider(jwt);

        return userRepository.findByCognitoSub(sub)
                .map(user -> {
                    user.updateName(name);
                    return UserResponse.from(user);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .cognitoSub(sub)
                            .provider(provider)
                            .role(User.Role.PATIENT)
                            .build();
                    return UserResponse.from(userRepository.save(newUser));
                });
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Jwt jwt) {
        String sub = jwt.getSubject();
        User user = userRepository.findByCognitoSub(sub)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    // 기존 이메일/비밀번호 회원가입 (LOCAL provider)
    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(request.getRole() != null ? request.getRole() : User.Role.PATIENT)
                .provider(User.Provider.LOCAL)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return UserResponse.from(user);
    }

    /**
     * Cognito JWT의 identities 클레임 또는 iss로 provider 식별
     * Cognito는 소셜 로그인 시 "cognito:username" 앞에 "Google_" 또는 "KakaoOIDC_" 등을 붙입니다.
     */
    private User.Provider resolveProvider(Jwt jwt) {
        String username = jwt.getClaimAsString("cognito:username");
        if (username != null) {
            if (username.startsWith("Google_")) return User.Provider.GOOGLE;
            if (username.startsWith("KakaoOIDC_")) return User.Provider.KAKAO;
        }
        return User.Provider.LOCAL;
    }
}
