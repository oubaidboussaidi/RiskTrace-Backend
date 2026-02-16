package com.risktrace.alert_service.Controller;

import com.risktrace.alert_service.Model.Alert;
import com.risktrace.alert_service.Repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private AlertRepository alertRepository;

    @GetMapping
    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    @PostMapping
    public Alert createAlert(@RequestBody Alert alert) {
        if (alert.getTimestamp() == null) {
            alert.setTimestamp(new java.util.Date());
        }
        return alertRepository.save(alert);
    }

    @GetMapping("/active")
    public List<Alert> getActiveAlerts() {
        // In a real app, use a custom query in repository
        return alertRepository.findAll().stream()
                .filter(a -> "OPEN".equals(a.getStatus())
                        && ("HIGH".equals(a.getSeverity()) || "CRITICAL".equals(a.getSeverity())))
                .collect(java.util.stream.Collectors.toList());
    }

    @PutMapping("/{id}/status")
    public Alert updateAlertStatus(@PathVariable String id, @RequestBody java.util.Map<String, String> update) {
        return alertRepository.findById(id).map(alert -> {
            if (update.containsKey("status"))
                alert.setStatus(update.get("status"));
            // Add assignee logic if needed later
            return alertRepository.save(alert);
        }).orElse(null);
    }

}
