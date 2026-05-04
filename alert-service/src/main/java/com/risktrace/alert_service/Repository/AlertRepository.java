package com.risktrace.alert_service.Repository;

import com.risktrace.alert_service.Model.Alert;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends MongoRepository<Alert, String> {

    /** All alerts for a given organization (tenant-scoped) */
    List<Alert> findByOrganizationId(String organizationId);

    /** Alerts for a given organization filtered by status */
    List<Alert> findByOrganizationIdAndStatus(String organizationId, String status);

    /** Alerts for a given organization filtered by severity */
    List<Alert> findByOrganizationIdAndSeverity(String organizationId, String severity);

    /** Alerts for a specific site */
    List<Alert> findBySiteId(String siteId);

    /** Alerts for a specific site within an organization */
    List<Alert> findByOrganizationIdAndSiteId(String organizationId, String siteId);
}
