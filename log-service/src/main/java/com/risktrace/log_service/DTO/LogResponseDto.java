package com.risktrace.log_service.DTO;

import lombok.Data;

@Data
public class LogResponseDto {
    private String id;
    private String siteId;
    private String organizationId;
    private String sessionId;
    private String type;
    private String url;
    private String method;
    private Integer statusCode;
    private String userAgent;
    private String device;
    private Long responseTime;
    private String createdAt;
    private String ipAddress;
    private String country;
    private String city;
    private Double anomalyScore;
    private Boolean isAnomaly;
    private Boolean isSuspicious; // User marked it
}
