package com.checkdang.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    private String token;    // PK (UUID)
    private String userId;
    private Long expiresAt;  // Unix timestamp (DynamoDB TTL)
    private String createdAt;
}
