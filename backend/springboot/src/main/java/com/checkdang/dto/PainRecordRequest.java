package com.checkdang.dto;

import com.checkdang.domain.PainRecord;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PainRecordRequest {

    private PainRecord.BodyPart bodyPart;
    private Integer intensity;
    private List<PainRecord.PainType> painTypes;
}
