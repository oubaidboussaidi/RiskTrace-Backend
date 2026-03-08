package com.risktrace.log_service.Model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Read-only projection of the site document used ONLY by log-service
 * to validate an incoming apiKey and resolve the siteId and organizationId.
 * This avoids HTTP inter-service calls on the hot log-ingestion path.
 */
@Document(collection = "sites")
@Data
public class SiteRef {
    @Id
    private String id;
    private String apiKey;
    private String status;
    private String organizationId;
}
