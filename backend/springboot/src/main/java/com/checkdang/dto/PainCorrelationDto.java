package com.checkdang.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PainCorrelationDto {

    private String factor;
    private String level;        // HIGH / MEDIUM / LOW
    private String description;

    public enum CorrelationLevel {
        HIGH, MEDIUM, LOW
    }
}
