package com.risktrace.alert_service.DTO;

import lombok.Data;

import java.util.Date;

/**
 * AlertResponse — payload returned to the client for all alert reads.
 */
@Data
public class AlertResponse {
    private String id;
    private String organizationId;
    private String siteId;
    private String type;
    private String severity;
    private String description;
    private String sourceIp;
    private String targetPath;
    private String sessionId;
    private Double anomalyScore;
    private String status;
    private Date timestamp;
}
