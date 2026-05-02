package com.checkdang.security.oauth2;

import com.checkdang.domain.User;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String email;
        String name;
        String providerId;
        User.Provider provider;

        if ("google".equals(registrationId)) {
            email = oAuth2User.getAttribute("email");
            name = oAuth2User.getAttribute("name");
            providerId = oAuth2User.getAttribute("sub");
            provider = User.Provider.GOOGLE;
        } else if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            providerId = String.valueOf(oAuth2User.getAttribute("id"));
            String kakaoEmail = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
            email = (kakaoEmail != null) ? kakaoEmail : "kakao_" + providerId + "@checkdang.com";
            name = (String) profile.get("nickname");
            provider = User.Provider.KAKAO;
        } else {
            throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다.");
        }

        User.Provider finalProvider = provider;
        String finalProviderId = providerId;
        String finalEmail = email;
        String finalName = name;

        userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(User.builder()
                        .email(finalEmail)
                        .name(finalName)
                        .provider(finalProvider)
                        .providerId(finalProviderId)
                        .role(User.Role.PATIENT)
                        .build())
        );

        return oAuth2User;
    }
}
