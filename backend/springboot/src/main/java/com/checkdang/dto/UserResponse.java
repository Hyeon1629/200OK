package com.checkdang.dto;

import com.checkdang.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    private User.Role role;
    private User.Gender gender;
    private String birthDate;
    private Integer height;
    private Integer weight;
    private User.DiabetesType diabetesType;
    private Integer targetBloodSugar;
    private Boolean notificationEnabled;
    private Boolean isPremium;
    private Boolean isGuest;
    private String familyGroupId;
    private User.FamilyRole familyRole;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .gender(user.getGender())
                .birthDate(user.getBirthDate())
                .height(user.getHeight())
                .weight(user.getWeight())
                .diabetesType(user.getDiabetesType())
                .targetBloodSugar(user.getTargetBloodSugar())
                .notificationEnabled(user.getNotificationEnabled())
                .isPremium(user.getIsPremium() != null ? user.getIsPremium() : false)
                .isGuest(user.getIsGuest() != null ? user.getIsGuest() : false)
                .familyGroupId(user.getFamilyGroupId())
                .familyRole(user.getFamilyRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
