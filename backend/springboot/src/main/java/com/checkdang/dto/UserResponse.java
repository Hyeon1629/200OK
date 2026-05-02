package com.checkdang.dto;

import com.checkdang.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class UserResponse {
    private String id;
    private String email;
    private String name;
    private User.Role role;
    private Boolean isPremium;
    private Boolean isGuest;
    private String familyGroupId;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .isPremium(user.getIsPremium() != null ? user.getIsPremium() : false)
                .isGuest(user.getIsGuest() != null ? user.getIsGuest() : false)
                .familyGroupId(user.getFamilyGroupId())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
