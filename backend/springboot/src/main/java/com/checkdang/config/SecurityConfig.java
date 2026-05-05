package com.checkdang.config;

import com.checkdang.security.RateLimitFilter;
import com.checkdang.security.jwt.JwtAuthenticationFilter;
import com.checkdang.security.jwt.JwtTokenProvider;
import com.checkdang.security.oauth2.CustomOAuth2UserService;
import com.checkdang.security.oauth2.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 가능
                        .requestMatchers(
                                "/api/auth/**",
                                "/actuator/health",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // 비회원(GUEST) + 일반 회원 모두 접근 가능 (홈, 혈당기록)
                        .requestMatchers(
                                "/api/home/**",
                                "/api/records/blood-sugar/**"
                        ).hasAnyRole("GUEST", "PATIENT", "CAREGIVER", "ADMIN")
                        // 인슐린 기록은 로그인 회원만
                        .requestMatchers(
                                "/api/records/insulin/**"
                        ).hasAnyRole("PATIENT", "CAREGIVER", "ADMIN")
                        // 나머지는 정식 회원만
                        .anyRequest().hasAnyRole("PATIENT", "CAREGIVER", "ADMIN")
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(
                        new RateLimitFilter(),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
