package com.risktrace.alert_service.DTO;

import lombok.Data;

/**
 * AlertRequest — payload accepted when creating an alert manually or from log-service.
 */
@Data
public class AlertRequest {
    private String organizationId;
    private String siteId;
    private String type;
    private String severity;
    private String description;
    private String sourceIp;
    private String targetPath;
    private String sessionId;
    private Double anomalyScore;
    /** Defaults to OPEN when not specified. */
    private String status;
}
