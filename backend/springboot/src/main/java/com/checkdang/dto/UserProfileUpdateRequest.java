package com.checkdang.dto;

import com.checkdang.domain.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserProfileUpdateRequest {
    private String name;
    private User.Gender gender;
    private String birthDate;
    private Integer height;
    private Integer weight;
    private User.DiabetesType diabetesType;
    private Integer targetBloodSugar;
    private Boolean notificationEnabled;
}
