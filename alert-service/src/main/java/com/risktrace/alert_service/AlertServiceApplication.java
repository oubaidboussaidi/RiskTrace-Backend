package com.risktrace.alert_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * RiskTrace Alert Service
 *
 * <p>Manages incident lifecycle: creation, status updates, and SOC notifications.
 * {@code @EnableAsync} is required for non-blocking email dispatch in
 * {@link com.risktrace.alert_service.Service.IncidentNotificationService}.</p>
 */
@SpringBootApplication
@EnableAsync
public class AlertServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlertServiceApplication.class, args);
	}
}
