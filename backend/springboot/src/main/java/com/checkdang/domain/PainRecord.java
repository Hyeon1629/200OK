package com.checkdang.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "pain_records")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PainRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** 통증 강도 1~10 */
    @Column(nullable = false)
    private Integer intensity;

    /** 통증 부위 (프론트 BodyPart enum과 동일) */
    @Enumerated(EnumType.STRING)
    @Column(name = "body_part", nullable = false)
    private BodyPart bodyPart;

    /** 통증 종류 (복수 선택, CSV로 저장) */
    @Convert(converter = PainTypeListConverter.class)
    @Column(name = "pain_types", nullable = false)
    private List<PainType> painTypes;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "ai_cause", columnDefinition = "TEXT")
    private String aiCause;

    @Column(name = "ai_first_aid", columnDefinition = "TEXT")
    private String aiFirstAid;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ────────────────────────────────────────────────
    // Enum 정의 (프론트 enum 이름과 정확히 일치)
    // ────────────────────────────────────────────────

    public enum BodyPart {
        // 정면
        HEAD, NECK_FRONT,
        LEFT_SHOULDER_FRONT, RIGHT_SHOULDER_FRONT,
        CHEST, LEFT_ARM_FRONT, RIGHT_ARM_FRONT,
        ABDOMEN, LEFT_HIP_FRONT, RIGHT_HIP_FRONT,
        LEFT_THIGH_FRONT, RIGHT_THIGH_FRONT,
        LEFT_KNEE, RIGHT_KNEE,
        LEFT_SHIN, RIGHT_SHIN,
        // 후면
        NECK_BACK, UPPER_BACK, LOWER_BACK,
        LEFT_SHOULDER_BACK, RIGHT_SHOULDER_BACK
    }

    public enum PainType {
        SHARP, DULL, BURNING, THROBBING, STIFFNESS, NUMBNESS
    }

    // ────────────────────────────────────────────────
    // List<PainType> ↔ "SHARP,NUMBNESS" CSV 변환
    // ────────────────────────────────────────────────

    @Converter
    public static class PainTypeListConverter implements AttributeConverter<List<PainType>, String> {

        @Override
        public String convertToDatabaseColumn(List<PainType> attribute) {
            if (attribute == null || attribute.isEmpty()) return "";
            return attribute.stream().map(Enum::name).collect(Collectors.joining(","));
        }

        @Override
        public List<PainType> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return List.of();
            return Arrays.stream(dbData.split(","))
                    .map(PainType::valueOf)
                    .collect(Collectors.toList());
        }
    }
}
