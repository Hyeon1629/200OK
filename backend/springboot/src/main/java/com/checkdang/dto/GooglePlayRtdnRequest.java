package com.checkdang.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GooglePlayRtdnRequest {

    private Message message;
    private String subscription;

    @Getter
    @NoArgsConstructor
    public static class Message {
        private String data;        // Base64 인코딩된 알림 JSON
        private String messageId;
        private String publishTime;
    }
}
