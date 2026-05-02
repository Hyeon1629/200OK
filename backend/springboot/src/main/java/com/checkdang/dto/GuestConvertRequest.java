package com.checkdang.dto;

import com.checkdang.domain.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GuestConvertRequest {
    private String email;
    private String password;
    private String name;
    private User.Role role;
    private Boolean termsAgreed;
}
