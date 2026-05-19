package com.checkdang.dto;

import com.checkdang.domain.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {
    private String email;
    private String password;
    private String name;
    private User.Role role;
    private Boolean termsAgreed;

    // 온보딩 데이터 (회원가입 완료 시 함께 전송)
    private String birthDate;   // YYYY-MM-DD
    private User.Gender gender; // MALE / FEMALE
    private Integer height;     // cm
    private Integer weight;     // kg
}
