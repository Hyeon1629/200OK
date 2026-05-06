package com.checkdang.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "diet")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Diet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User는 DynamoDB 엔티티(String UUID)이므로 @ManyToOne FK 대신 userId를 컬럼으로 저장
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time", nullable = false)
    private MealType mealType;

    @Column(nullable = false)
    private String foodName;

    private Double calories;
    private Double carbohydrate;
    private Double protein;
    private Double totalFat;
    private Double sugar;
    private Double dietaryFiber;
    private Double sodium;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataSource dataSource;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum MealType {
        BREAKFAST, LUNCH, DINNER, SNACK, UNKNOWN
    }

    public enum DataSource {
        SAMSUNG_HEALTH, MANUAL
    }
}