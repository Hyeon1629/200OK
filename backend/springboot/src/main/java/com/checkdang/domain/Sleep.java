package com.checkdang.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sleeps")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Sleep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User는 DynamoDB 엔티티(String UUID)이므로 @ManyToOne FK 대신 userId를 컬럼으로 저장
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private LocalDateTime sleepStart;

    @Column(nullable = false)
    private LocalDateTime sleepEnd;

    @Column(nullable = false)
    private Long totalMinutes;

    private Double efficiency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataSource dataSource;

    @OneToMany(mappedBy = "sleep", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SleepStage> stages = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum DataSource {
        SAMSUNG_HEALTH, MANUAL
    }
}
