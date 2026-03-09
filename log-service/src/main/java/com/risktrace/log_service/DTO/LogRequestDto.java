package com.risktrace.log_service.DTO;

import lombok.Data;

@Data
public class LogRequestDto {
    private String apiKey;
    private String sessionId;
    private String type;
    private String url;
    private String method;
    private Integer statusCode;
    private String userAgent;
    private String device;
    private Long responseTime;
    private String createdAt;
}
