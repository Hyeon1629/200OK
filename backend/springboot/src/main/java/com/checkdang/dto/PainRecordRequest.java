package com.checkdang.dto;

import com.checkdang.domain.PainRecord;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class PainRecordRequest {

    /** 통증 강도 1~10 */
    private Integer intensity;

    /** 통증 부위 */
    private PainRecord.BodyArea bodyArea;

    /** 바디맵 앞면/뒷면 */
    private PainRecord.BodySide bodySide;

    /** 바디맵 X 좌표 (0.0~1.0 비율) */
    private Double bodyMapX;

    /** 바디맵 Y 좌표 (0.0~1.0 비율) */
    private Double bodyMapY;

    /** 통증 종류 */
    private PainRecord.PainType painType;

    /** 메모 (선택) */
    private String memo;

    /** 통증 발생 시각 — null이면 서버 현재 시각 */
    private LocalDateTime recordedAt;
}
