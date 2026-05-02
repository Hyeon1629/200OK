package com.checkdang.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "exercise_record")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    private String sourceId;

    @Column(name = "exercise_name", nullable = false)
    private String exerciseName;

    @Column(nullable = false)
    private Long duration;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    private Integer sets;
    private Integer reps;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "ai_result", columnDefinition = "TEXT")
    private String aiResult;

    @Column(name = "ai_recommendation", columnDefinition = "TEXT")
    private String aiRecommendation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataSource dataSource;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum DataSource {
        SAMSUNG_HEALTH, MANUAL
    }
}
