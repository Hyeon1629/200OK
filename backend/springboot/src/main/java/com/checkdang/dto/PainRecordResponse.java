package com.checkdang.dto;

import com.checkdang.domain.PainRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.ZoneId;
import java.util.List;

@Getter
@Builder
public class PainRecordResponse {

    private Long id;
    private PainRecord.BodyPart bodyPart;
    private Integer intensity;
    private List<PainRecord.PainType> painTypes;
    private Long recordedAt;   // Unix millis (프론트 요구사항)
    private String aiCause;
    private String aiFirstAid;

    public static PainRecordResponse from(PainRecord record) {
        return PainRecordResponse.builder()
                .id(record.getId())
                .bodyPart(record.getBodyPart())
                .intensity(record.getIntensity())
                .painTypes(record.getPainTypes())
                .recordedAt(record.getRecordedAt()
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant()
                        .toEpochMilli())
                .aiCause(record.getAiCause())
                .aiFirstAid(record.getAiFirstAid())
                .build();
    }
}
