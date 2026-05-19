package com.checkdang.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

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

    /** 통증 부위 (바디맵 선택 위치) */
    @Enumerated(EnumType.STRING)
    @Column(name = "body_area", nullable = false)
    private BodyArea bodyArea;

    /** 바디맵 앞면/뒷면 */
    @Enumerated(EnumType.STRING)
    @Column(name = "body_side", nullable = false)
    private BodySide bodySide;

    /** 바디맵 X 좌표 (0.0~1.0 비율, 앱에서 터치 위치) */
    @Column(name = "body_map_x")
    private Double bodyMapX;

    /** 바디맵 Y 좌표 (0.0~1.0 비율, 앱에서 터치 위치) */
    @Column(name = "body_map_y")
    private Double bodyMapY;

    /** 통증 종류 */
    @Enumerated(EnumType.STRING)
    @Column(name = "pain_type", nullable = false)
    private PainType painType;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum BodyArea {
        // 머리/목
        HEAD,           // 머리
        NECK,           // 목
        // 상체 앞
        CHEST,          // 가슴
        UPPER_ABDOMEN,  // 상복부
        LOWER_ABDOMEN,  // 하복부
        // 상체 뒤
        UPPER_BACK,     // 등 위
        LOWER_BACK,     // 허리
        // 어깨/팔
        LEFT_SHOULDER,  // 왼쪽 어깨
        RIGHT_SHOULDER, // 오른쪽 어깨
        LEFT_UPPER_ARM, // 왼쪽 위팔
        RIGHT_UPPER_ARM,// 오른쪽 위팔
        LEFT_ELBOW,     // 왼쪽 팔꿈치
        RIGHT_ELBOW,    // 오른쪽 팔꿈치
        LEFT_FOREARM,   // 왼쪽 아래팔
        RIGHT_FOREARM,  // 오른쪽 아래팔
        LEFT_WRIST,     // 왼쪽 손목
        RIGHT_WRIST,    // 오른쪽 손목
        LEFT_HAND,      // 왼쪽 손
        RIGHT_HAND,     // 오른쪽 손
        // 골반/엉덩이
        LEFT_HIP,       // 왼쪽 엉덩이
        RIGHT_HIP,      // 오른쪽 엉덩이
        // 다리
        LEFT_THIGH,     // 왼쪽 허벅지
        RIGHT_THIGH,    // 오른쪽 허벅지
        LEFT_KNEE,      // 왼쪽 무릎
        RIGHT_KNEE,     // 오른쪽 무릎
        LEFT_SHIN,      // 왼쪽 정강이
        RIGHT_SHIN,     // 오른쪽 정강이
        LEFT_ANKLE,     // 왼쪽 발목
        RIGHT_ANKLE,    // 오른쪽 발목
        LEFT_FOOT,      // 왼쪽 발
        RIGHT_FOOT      // 오른쪽 발
    }

    public enum BodySide {
        FRONT,  // 앞면
        BACK    // 뒷면
    }

    public enum PainType {
        SHARP,      // 찌르는 통증
        DULL,       // 둔한 통증
        BURNING,    // 타는 듯한 통증
        THROBBING,  // 욱신거리는 통증
        ACHING,     // 쑤시는 통증
        CRAMPING,   // 쥐어짜는 통증
        STABBING,   // 칼로 찌르는 통증
        PRESSURE    // 압박감
    }
}
