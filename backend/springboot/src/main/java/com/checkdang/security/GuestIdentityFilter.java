package com.checkdang.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;
import software.amazon.awssdk.services.cognitoidentity.model.CognitoIdentityException;
import software.amazon.awssdk.services.cognitoidentity.model.DescribeIdentityRequest;
import software.amazon.awssdk.services.cognitoidentity.model.DescribeIdentityResponse;

import java.io.IOException;
import java.util.List;

/**
 * Cognito Identity Pool 비인증 ID를 검증하여 게스트 접근을 허용하는 필터.
 * 앱이 Cognito Identity Pool에서 받은 Identity ID를 X-Guest-Identity-Id 헤더로 전송.
 * DescribeIdentity 호출로 실제 존재하는 비인증 ID인지 확인.
 */
@RequiredArgsConstructor
@Slf4j
public class GuestIdentityFilter extends OncePerRequestFilter {

    private final CognitoIdentityClient cognitoIdentityClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 이미 인증된 경우(Cognito User Pool JWT) 게스트 필터 건너뜀
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identityId = request.getHeader("X-Guest-Identity-Id");
        if (identityId == null || identityId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            DescribeIdentityResponse desc = cognitoIdentityClient.describeIdentity(
                    DescribeIdentityRequest.builder().identityId(identityId).build());

            // logins가 비어있어야 비인증(게스트) Identity
            if (desc.identityId() != null &&
                    (desc.logins() == null || desc.logins().isEmpty())) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        identityId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_GUEST")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (CognitoIdentityException e) {
            log.debug("유효하지 않은 Guest Identity ID: {}", identityId);
        }

        filterChain.doFilter(request, response);
    }
}
