package com.checkdang.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncResponse {

    private int saved;
    private int skipped;
    private int total;

    public static SyncResponse of(int saved, int total) {
        return SyncResponse.builder()
                .saved(saved)
                .skipped(total - saved)
                .total(total)
                .build();
    }
}