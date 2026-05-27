package com.checkdang.dto;

import com.checkdang.domain.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserProfileUpdateRequest {
    private String name;
    private String birthDate;
    private User.Gender gender;  // MALE / FEMALE / NONE
    private Integer height;
    private Integer weight;
}
