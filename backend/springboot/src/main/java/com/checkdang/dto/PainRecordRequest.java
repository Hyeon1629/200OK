package com.checkdang.dto;

import com.checkdang.domain.PainRecord;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PainRecordRequest {

    private PainRecord.BodyPart bodyPart;
    private Integer intensity;                  // 1~5
    private List<String> qualityTags;           // 통증 성질 (PainTaxonomy.QUALITY 한글 태그, 빈 리스트 허용)
    private List<String> situationTags;         // 통증 상황 (PainTaxonomy.SITUATION 한글 태그, 빈 리스트 허용)
}
