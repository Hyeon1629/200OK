package com.checkdang.dto;

import com.checkdang.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FamilyMemberResponse {

    private Long id;
    private String name;
    private String email;
    private User.Role role;
    private User.FamilyRole familyRole;
    private User.Gender gender;
    private String birthDate;
    private Integer height;
    private Integer weight;
    private User.DiabetesType diabetesType;

    public static FamilyMemberResponse from(User user) {
        return FamilyMemberResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .familyRole(user.getFamilyRole())
                .gender(user.getGender())
                .birthDate(user.getBirthDate())
                .height(user.getHeight())
                .weight(user.getWeight())
                .diabetesType(user.getDiabetesType())
                .build();
    }
}
