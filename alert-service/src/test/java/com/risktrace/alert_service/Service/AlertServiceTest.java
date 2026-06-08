package com.risktrace.alert_service.Service;

import com.risktrace.alert_service.DTO.AlertRequest;
import com.risktrace.alert_service.DTO.AlertResponse;
import com.risktrace.alert_service.DTO.EscalationRequest;
import com.risktrace.alert_service.Model.Alert;
import com.risktrace.alert_service.Repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private IncidentNotificationService notificationService;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateAlert_Success() {
        AlertRequest request = new AlertRequest();
        request.setOrganizationId("org123");
        request.setSiteId("site123");
        request.setType("ANOMALY");
        request.setSeverity("HIGH");
        request.setDescription("Test Alert");

        Alert savedAlert = new Alert();
        savedAlert.setId("alert123");
        savedAlert.setOrganizationId("org123");
        savedAlert.setSiteId("site123");
        savedAlert.setType("ANOMALY");
        savedAlert.setSeverity("HIGH");
        savedAlert.setDescription("Test Alert");
        savedAlert.setStatus("OPEN");

        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);

        AlertResponse response = alertService.createAlert(request);

        assertNotNull(response);
        assertEquals("alert123", response.getId());
        assertEquals("OPEN", response.getStatus());
        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void testCreateAlert_MissingOrgId() {
        AlertRequest request = new AlertRequest();
        request.setOrganizationId(null);

        assertThrows(ResponseStatusException.class, () -> alertService.createAlert(request));
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void testUpdateAlertStatus_Success() {
        String alertId = "alert123";
        Alert existingAlert = new Alert();
        existingAlert.setId(alertId);
        existingAlert.setStatus("OPEN");

        Alert updatedAlert = new Alert();
        updatedAlert.setId(alertId);
        updatedAlert.setStatus("RESOLVED");

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(existingAlert));
        when(alertRepository.save(any(Alert.class))).thenReturn(updatedAlert);

        AlertResponse response = alertService.updateAlertStatus(alertId, "RESOLVED");

        assertNotNull(response);
        assertEquals("RESOLVED", response.getStatus());
        verify(alertRepository, times(1)).findById(alertId);
        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void testUpdateAlertStatus_NotFound() {
        String alertId = "invalid_id";
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> alertService.updateAlertStatus(alertId, "RESOLVED"));
        verify(alertRepository, times(1)).findById(alertId);
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void testEscalateAlert_Success() {
        String alertId = "alert123";
        Alert alert = new Alert();
        alert.setId(alertId);
        alert.setOrganizationId("org123");

        EscalationRequest request = new EscalationRequest();
        request.setAnalystName("Analyst A");
        request.setMessage("Escalating this critical alert");

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(userClient.getOrganizationOwnerEmail("org123")).thenReturn("owner@example.com");
        doNothing().when(notificationService).sendSocIncidentNotification(any(), anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> alertService.escalateAlert(alertId, request));

        verify(alertRepository, times(1)).findById(alertId);
        verify(userClient, times(1)).getOrganizationOwnerEmail("org123");
        verify(notificationService, times(1)).sendSocIncidentNotification(alert, "Analyst A", "Escalating this critical alert", "owner@example.com");
    }

    @Test
    void testEscalateAlert_NotFound() {
        String alertId = "invalid_id";
        EscalationRequest request = new EscalationRequest();

        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> alertService.escalateAlert(alertId, request));
        verify(alertRepository, times(1)).findById(alertId);
        verify(userClient, never()).getOrganizationOwnerEmail(anyString());
    }

    @Test
    void testGetAlertsByOrganization() {
        String orgId = "org123";
        Alert alert = new Alert();
        alert.setId("1");
        alert.setOrganizationId(orgId);

        when(alertRepository.findByOrganizationId(orgId)).thenReturn(Arrays.asList(alert));

        List<AlertResponse> responses = alertService.getAlertsByOrganization(orgId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(orgId, responses.get(0).getOrganizationId());
        verify(alertRepository, times(1)).findByOrganizationId(orgId);
    }

    @Test
    void testGetActiveAlertsByOrganization() {
        String orgId = "org123";
        Alert alert = new Alert();
        alert.setId("1");
        alert.setOrganizationId(orgId);
        alert.setStatus("OPEN");

        when(alertRepository.findByOrganizationIdAndStatus(orgId, "OPEN")).thenReturn(Arrays.asList(alert));

        List<AlertResponse> responses = alertService.getActiveAlertsByOrganization(orgId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("OPEN", responses.get(0).getStatus());
        verify(alertRepository, times(1)).findByOrganizationIdAndStatus(orgId, "OPEN");
    }
}
