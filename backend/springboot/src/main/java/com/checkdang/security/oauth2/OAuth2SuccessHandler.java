package com.checkdang.security.oauth2;

import com.checkdang.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = extractEmail(oAuth2User);
        String token = jwtTokenProvider.generateToken(email);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String extractEmail(OAuth2User oAuth2User) {
        if (oAuth2User.getAttribute("email") != null) {
            return oAuth2User.getAttribute("email");
        }
        // Kakao
        java.util.Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
        String kakaoEmail = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        if (kakaoEmail != null) return kakaoEmail;
        // 이메일 권한 없는 경우 kakao ID 기반 이메일 사용
        return "kakao_" + oAuth2User.getAttribute("id") + "@checkdang.com";
    }
}
