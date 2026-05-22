package com.checkdang.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CognitoService {

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    /**
     * 기존 로컬 회원을 Cognito User Pool에 등록하고 비밀번호 재설정 이메일 발송.
     * 이미 Cognito에 존재하면 재설정 이메일만 발송.
     */
    public void migrateLocalUser(String email) {
        try {
            cognitoClient.adminCreateUser(r -> r
                    .userPoolId(userPoolId)
                    .username(email)
                    .messageAction(MessageActionType.SUPPRESS)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("email_verified").value("true").build()
                    ));
            log.info("Cognito 사용자 생성: {}", email);
        } catch (UsernameExistsException e) {
            log.info("이미 Cognito에 존재하는 사용자: {}", email);
        }

        cognitoClient.adminResetUserPassword(r -> r
                .userPoolId(userPoolId)
                .username(email));
        log.info("비밀번호 재설정 이메일 발송: {}", email);
    }
}
