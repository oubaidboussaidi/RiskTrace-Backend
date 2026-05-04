package com.risktrace.alert_service.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

@Document(collection = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    private String id;

    /** Organization that owns this alert — used for tenant-scoped queries */
    @Indexed
    private String organizationId;

    /** The specific site that triggered the alert */
    private String siteId;

    /** Alert type: ANOMALY_DETECTED, BRUTE_FORCE, SQL_INJECTION, SUSPICIOUS_ACTIVITY, etc. */
    private String type;

    /** Severity level: CRITICAL, HIGH, MEDIUM, LOW */
    private String severity;

    /** Human-readable description of the detected behaviour */
    private String description;

    /** Source IP address of the suspicious traffic */
    private String sourceIp;

    /** URL path targeted by the suspicious request */
    private String targetPath;

    /** Session ID associated with the alert */
    private String sessionId;

    /** Anomaly score produced by the ML model (0.0–1.0), nullable for manual incidents */
    private Double anomalyScore;

    /** Lifecycle status: OPEN | IN_PROGRESS | RESOLVED | IGNORED */
    private String status;

    /** Creation timestamp */
    private Date timestamp;
}
