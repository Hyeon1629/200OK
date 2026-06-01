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

    /** 통증 강도 1~5 (프론트 슬라이더와 동일) */
    @Column(nullable = false)
    private Integer intensity;

    /** 통증 부위 (프론트 BodyPart enum과 동일) */
    @Enumerated(EnumType.STRING)
    @Column(name = "body_part", nullable = false)
    private BodyPart bodyPart;

    /** 통증 성질 태그 (프론트 PainTaxonomy.QUALITY 한글 태그, 복수, CSV 저장, 빈 리스트 허용) */
    @Convert(converter = StringListConverter.class)
    @Column(name = "quality_tags", columnDefinition = "TEXT")
    private List<String> qualityTags;

    /** 통증 상황 태그 (프론트 PainTaxonomy.SITUATION 한글 태그, 복수, CSV 저장, 빈 리스트 허용) */
    @Convert(converter = StringListConverter.class)
    @Column(name = "situation_tags", columnDefinition = "TEXT")
    private List<String> situationTags;

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

    // ────────────────────────────────────────────────
    // List<String> ↔ "태그1,태그2" CSV 변환 (성질·상황 태그 공용)
    // 프론트 PainTaxonomy 태그는 콤마를 포함하지 않는 고정 어휘
    // ────────────────────────────────────────────────

    @Converter
    public static class StringListConverter implements AttributeConverter<List<String>, String> {

        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            if (attribute == null || attribute.isEmpty()) return "";
            return String.join(",", attribute);
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return List.of();
            return Arrays.stream(dbData.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }
}
