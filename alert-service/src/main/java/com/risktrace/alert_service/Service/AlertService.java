package com.risktrace.alert_service.Service;

import com.risktrace.alert_service.DTO.AlertRequest;
import com.risktrace.alert_service.DTO.AlertResponse;
import com.risktrace.alert_service.Model.Alert;
import com.risktrace.alert_service.Repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlertService {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private IncidentNotificationService notificationService;

    @Autowired
    private UserClient userClient;

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    public AlertResponse createAlert(AlertRequest request) {
        if (request.getOrganizationId() == null || request.getOrganizationId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization ID is required");
        }

        Alert alert = new Alert();
        alert.setOrganizationId(request.getOrganizationId());
        alert.setSiteId(request.getSiteId());
        alert.setType(request.getType() != null ? request.getType() : "SUSPICIOUS_ACTIVITY");
        alert.setSeverity(request.getSeverity() != null ? request.getSeverity() : "MEDIUM");
        alert.setDescription(request.getDescription());
        alert.setSourceIp(request.getSourceIp());
        alert.setTargetPath(request.getTargetPath());
        alert.setSessionId(request.getSessionId());
        alert.setAnomalyScore(request.getAnomalyScore());
        alert.setStatus(request.getStatus() != null ? request.getStatus() : "OPEN");
        alert.setTimestamp(new Date());

        Alert savedAlert = alertRepository.save(alert);
        return mapToResponse(savedAlert);
    }

    public AlertResponse updateAlertStatus(String id, String status) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));

        alert.setStatus(status);
        Alert updatedAlert = alertRepository.save(alert);
        return mapToResponse(updatedAlert);
    }

    public void escalateAlert(String id, com.risktrace.alert_service.DTO.EscalationRequest request) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        
        // Fetch the owner's email to ensure the right person is notified
        String ownerEmail = userClient.getOrganizationOwnerEmail(alert.getOrganizationId());
        
        notificationService.sendSocIncidentNotification(alert, request.getAnalystName(), request.getMessage(), ownerEmail);
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    public List<AlertResponse> getAllAlerts() {
        return alertRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> getAlertsByOrganization(String organizationId) {
        return alertRepository.findByOrganizationId(organizationId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> getActiveAlertsByOrganization(String organizationId) {
        return alertRepository.findByOrganizationIdAndStatus(organizationId, "OPEN").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> getAlertsBySite(String siteId) {
        return alertRepository.findBySiteId(siteId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> getActiveAlerts() {
        return alertRepository.findAll().stream()
                .filter(a -> "OPEN".equals(a.getStatus()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private AlertResponse mapToResponse(Alert alert) {
        AlertResponse response = new AlertResponse();
        response.setId(alert.getId());
        response.setOrganizationId(alert.getOrganizationId());
        response.setSiteId(alert.getSiteId());
        response.setType(alert.getType());
        response.setSeverity(alert.getSeverity());
        response.setDescription(alert.getDescription());
        response.setSourceIp(alert.getSourceIp());
        response.setTargetPath(alert.getTargetPath());
        response.setSessionId(alert.getSessionId());
        response.setAnomalyScore(alert.getAnomalyScore());
        response.setStatus(alert.getStatus());
        response.setTimestamp(alert.getTimestamp());
        return response;
    }
}
