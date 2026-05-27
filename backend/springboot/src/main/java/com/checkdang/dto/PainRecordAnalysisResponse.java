package com.checkdang.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PainRecordAnalysisResponse {

    private PainRecordResponse record;
    private String summary;
    private List<PainCorrelationDto> correlations;
    private String recommendation;
}
