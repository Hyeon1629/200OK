package com.checkdang.config;

import com.checkdang.security.GuestIdentityFilter;
import com.checkdang.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CognitoIdentityClient cognitoIdentityClient;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 어드민 전용 (permitAll보다 먼저 선언해야 /api/auth/** 와 충돌 안 함)
                        .requestMatchers("/api/auth/internal/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/auth/**",
                                "/actuator/health",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api/payment/kakao/cancel",
                                "/api/payment/kakao/fail",
                                "/api/payment/google/rtdn"
                        ).permitAll()
                        .requestMatchers("/api/home/**")
                                .hasAnyRole("GUEST", "PATIENT", "CAREGIVER", "ADMIN")
                        .requestMatchers("/api/records/insulin/**")
                                .hasAnyRole("PATIENT", "CAREGIVER", "ADMIN")
                        .anyRequest().hasAnyRole("PATIENT", "CAREGIVER", "ADMIN")
                )
                // Cognito User Pool RS256 JWT 검증
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(cognitoJwtConverter()))
                )
                // RateLimit → GuestIdentity → Cognito RS256 순서로 처리
                .addFilterBefore(new RateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new GuestIdentityFilter(cognitoIdentityClient),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // Cognito JWT의 cognito:groups 클레임 → Spring Security ROLE_xxx 변환
    // cognito:groups 없으면 기본 ROLE_PATIENT 부여 (소셜 로그인 일반 사용자)
    @Bean
    public JwtAuthenticationConverter cognitoJwtConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("cognito:groups");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = authoritiesConverter.convert(jwt);
            if (authorities == null || authorities.isEmpty()) {
                return java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PATIENT")
                );
            }
            return authorities;
        });
        return converter;
    }
}
