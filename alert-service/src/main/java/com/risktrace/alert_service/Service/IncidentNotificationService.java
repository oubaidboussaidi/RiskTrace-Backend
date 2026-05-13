package com.risktrace.alert_service.Service;

import com.risktrace.alert_service.Model.Alert;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * IncidentNotificationService — sends a branded HTML email to the SOC team
 * whenever an analyst manually escalates an incident through the platform.
 *
 * <p>All methods are {@code @Async} and do not block the calling HTTP thread.
 * Email failures are logged but never propagate to the caller.</p>
 *
 * <p>HTML rendering is fully delegated to the Thymeleaf template at
 * {@code resources/templates/email/incident-escalation.html}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentNotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.soc-email}")
    private String socEmail;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends a formatted SOC notification email for an escalated incident.
     * Executed asynchronously — the HTTP response is never blocked by mail delivery.
     *
     * @param alert          the persisted {@link Alert} document being escalated
     * @param analystName    the name of the analyst escalating the alert
     * @param customMessage  an optional message from the analyst to the org owner
     * @param recipientEmail target address; falls back to {@code app.soc-email} if blank
     */
    @Async
    public void sendSocIncidentNotification(Alert alert, String analystName,
                                            String customMessage, String recipientEmail) {
        String subject = buildSubject(alert);
        String html    = renderTemplate(alert, analystName, customMessage);
        String to      = (recipientEmail != null && !recipientEmail.isBlank()) ? recipientEmail : socEmail;
        dispatch(to, subject, html, alert.getId());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildSubject(Alert alert) {
        return "[RiskTrace] Threat Escalation Notice — "
                + nullSafe(alert.getSeverity(), "UNKNOWN")
                + " | " + nullSafe(alert.getType(), "INCIDENT")
                + " | ID: " + nullSafe(alert.getId(), "N/A");
    }

    private String renderTemplate(Alert alert, String analystName, String customMessage) {
        String severity = nullSafe(alert.getSeverity(), "UNKNOWN").toUpperCase();

        Context ctx = new Context();
        ctx.setVariable("severity",       severity);
        ctx.setVariable("severityColor",  resolveSeverityColor(severity));
        ctx.setVariable("alertId",        nullSafe(alert.getId(), "N/A"));
        ctx.setVariable("alertType",      nullSafe(alert.getType(), "N/A"));
        ctx.setVariable("anomalyScore",   alert.getAnomalyScore() != null
                                          ? String.format("%.2f", alert.getAnomalyScore()) : "N/A");
        ctx.setVariable("timestamp",      formatTimestamp(alert.getTimestamp()));
        ctx.setVariable("analystName",    nullSafe(analystName, "Unknown Analyst"));
        ctx.setVariable("customMessage",  nullSafe(customMessage, "Please investigate this incident immediately."));
        ctx.setVariable("sourceIp",       alert.getSourceIp());
        ctx.setVariable("targetPath",     alert.getTargetPath());
        ctx.setVariable("sessionId",      alert.getSessionId());
        ctx.setVariable("organizationId", nullSafe(alert.getOrganizationId(), "N/A"));
        ctx.setVariable("siteId",         alert.getSiteId());

        return templateEngine.process("email/incident-escalation", ctx);
    }

    private void dispatch(String to, String subject, String html, String alertId) {
        try {
            if (to == null || to.isBlank()) {
                log.warn("Skipping email dispatch for alert {} — no recipient address specified", alertId);
                return;
            }
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, "RiskTrace SOC");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Escalation notification dispatched — alert ID: {}, recipient: {}", alertId, to);
        } catch (Exception ex) {
            log.error("CRITICAL: Failed to dispatch escalation for alert {} (Recipient: {}): {}",
                      alertId, to, ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Severity utilities
    // -------------------------------------------------------------------------

    private String resolveSeverityColor(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "#dc2626";
            case "HIGH"     -> "#ea580c";
            case "MEDIUM"   -> "#d97706";
            case "LOW"      -> "#16a34a";
            default         -> "#8b949e";
        };
    }

    // -------------------------------------------------------------------------
    // General utilities
    // -------------------------------------------------------------------------

    private String formatTimestamp(java.util.Date date) {
        if (date == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date) + " UTC";
    }

    private String nullSafe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
