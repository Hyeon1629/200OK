package com.checkdang.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "insulin_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsulinRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insulin_type", length = 100, nullable = false)
    private String insulinType;

    @Column(name = "dosage", precision = 5, scale = 2, nullable = false)
    private BigDecimal dosage;

    @Column(name = "injected_at", nullable = false)
    private LocalDateTime injectedAt;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
