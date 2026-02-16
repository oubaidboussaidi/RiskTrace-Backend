package com.risktrace.log_service.Model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Read-only projection of the site document used ONLY by log-service
 * to validate an incoming apiKey and resolve the siteId.
 * This avoids HTTP inter-service calls on the hot log-ingestion path.
 *
 * Note: log-service must be configured to connect to the SAME MongoDB
 * database as site-service (or share a database), or this can be replaced
 * with an HTTP call to site-service in the future.
 */
@Document(collection = "sites")
@Data
public class SiteRef {
    @Id
    private String id;
    private String apiKey;
    private String status;
}
