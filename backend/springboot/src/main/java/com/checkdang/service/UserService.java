package com.checkdang.service;

import com.checkdang.domain.RefreshToken;
import com.checkdang.domain.User;
import com.checkdang.dto.GuestConvertRequest;
import com.checkdang.dto.LoginRequest;
import com.checkdang.dto.SignupRequest;
import com.checkdang.dto.TokenResponse;
import com.checkdang.dto.UserProfileUpdateRequest;
import com.checkdang.dto.UserResponse;
import com.checkdang.repository.RefreshTokenRepository;
import com.checkdang.repository.UserRepository;
import com.checkdang.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (!Boolean.TRUE.equals(request.getTermsAgreed())) {
            throw new IllegalArgumentException("이용약관에 동의해야 합니다.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(request.getRole() != null ? request.getRole() : User.Role.PATIENT)
                .provider(User.Provider.LOCAL)
                .isGuest(false)
                .termsAgreed(true)
                .accountStatus(User.AccountStatus.ACTIVE)
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .height(request.getHeight())
                .weight(request.getWeight())
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getEmail());
        String refreshToken = createRefreshToken(String.valueOf(user.getId()));

        return TokenResponse.of(accessToken, refreshToken, UserResponse.from(user));
    }

    @Transactional
    public TokenResponse guestConvert(GuestConvertRequest request) {
        if (!Boolean.TRUE.equals(request.getTermsAgreed())) {
            throw new IllegalArgumentException("이용약관에 동의해야 합니다.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(request.getRole() != null ? request.getRole() : User.Role.PATIENT)
                .provider(User.Provider.LOCAL)
                .isGuest(false)
                .termsAgreed(true)
                .accountStatus(User.AccountStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        String accessToken = jwtTokenProvider.generateToken(savedUser.getEmail());
        String refreshToken = createRefreshToken(String.valueOf(savedUser.getId()));

        return TokenResponse.of(accessToken, refreshToken, UserResponse.from(savedUser));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    public UserResponse getProfile(String email) {
        return UserResponse.from(userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다.")));
    }

    @Transactional
    public UserResponse updateProfile(String email, UserProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (request.getName() != null) user.setName(request.getName());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getBirthDate() != null) user.setBirthDate(request.getBirthDate());
        if (request.getHeight() != null) user.setHeight(request.getHeight());
        if (request.getWeight() != null) user.setWeight(request.getWeight());
        if (request.getDiabetesType() != null) user.setDiabetesType(request.getDiabetesType());
        if (request.getTargetBloodSugar() != null) user.setTargetBloodSugar(request.getTargetBloodSugar());
        if (request.getNotificationEnabled() != null) user.setNotificationEnabled(request.getNotificationEnabled());

        return UserResponse.from(userRepository.save(user));
    }

    public boolean checkEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    public TokenResponse generateGuestToken() {
        String token = jwtTokenProvider.generateGuestToken();
        return TokenResponse.ofGuest(token);
    }

    private String createRefreshToken(String userId) {
        String token = UUID.randomUUID().toString();
        long expiresAt = Instant.now().getEpochSecond() + (refreshExpiration / 1000);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(token)
                .userId(userId)
                .expiresAt(expiresAt)
                .createdAt(Instant.now().toString())
                .build());

        return token;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword() != null ? user.getPassword() : "")
                .roles(user.getRole().name())
                .build();
    }
}
