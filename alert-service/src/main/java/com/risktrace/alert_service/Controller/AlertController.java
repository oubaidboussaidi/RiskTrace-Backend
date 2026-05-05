package com.risktrace.alert_service.Controller;

import com.risktrace.alert_service.DTO.AlertRequest;
import com.risktrace.alert_service.DTO.AlertResponse;
import com.risktrace.alert_service.Service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private AlertService alertService;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAllAlerts() {
        return ResponseEntity.ok(alertService.getAllAlerts());
    }

    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(@RequestBody AlertRequest alert) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.createAlert(alert));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<AlertResponse>> getAlertsByOrganization(@PathVariable String organizationId) {
        return ResponseEntity.ok(alertService.getAlertsByOrganization(organizationId));
    }

    @GetMapping("/active/{organizationId}")
    public ResponseEntity<List<AlertResponse>> getActiveAlertsByOrganization(@PathVariable String organizationId) {
        return ResponseEntity.ok(alertService.getActiveAlertsByOrganization(organizationId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<AlertResponse>> getActiveAlerts() {
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<AlertResponse>> getAlertsBySite(@PathVariable String siteId) {
        return ResponseEntity.ok(alertService.getAlertsBySite(siteId));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<AlertResponse> updateAlertStatus(@PathVariable String id, @RequestBody Map<String, String> update) {
        if (!update.containsKey("status")) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(alertService.updateAlertStatus(id, update.get("status")));
    }

    @PostMapping("/{id}/escalate")
    public ResponseEntity<Void> escalateAlert(@PathVariable String id, @RequestBody com.risktrace.alert_service.DTO.EscalationRequest request) {
        alertService.escalateAlert(id, request);
        return ResponseEntity.ok().build();
    }
}
