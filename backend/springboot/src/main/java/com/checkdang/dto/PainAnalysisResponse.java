package com.checkdang.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PainAnalysisResponse {
    private Long painRecordId;
    private String aiCause;
    private String aiFirstAid;
}
