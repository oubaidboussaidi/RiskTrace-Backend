package com.risktrace.alert_service.Service;

import com.risktrace.alert_service.Model.Alert;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * IncidentNotificationService — sends a branded HTML email to the SOC team
 * whenever an analyst manually reports an incident through the platform.
 *
 * <p>All methods are {@code @Async} and do not block the calling HTTP thread.
 * Email failures are logged but never propagate to the caller.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentNotificationService {

    private final JavaMailSender mailSender;

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
     * @param alert the persisted {@link Alert} document being escalated
     * @param analystName the name of the analyst escalating the alert
     * @param customMessage an optional message from the analyst to the org owner
     */
    @Async
    public void sendSocIncidentNotification(Alert alert, String analystName, String customMessage, String recipientEmail) {
        String subject = buildSubject(alert);
        String html    = buildEmailHtml(alert, analystName, customMessage);
        
        // If recipientEmail is provided, use it; otherwise fallback to SOC email
        String to = (recipientEmail != null && !recipientEmail.isBlank()) ? recipientEmail : socEmail;
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

    private void dispatch(String to, String subject, String html, String alertId) {
        try {
            if (to == null || to.isBlank()) {
                log.warn("Skipping email dispatch for alert {} - no recipient address specified", alertId);
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "RiskTrace SOC");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Escalation notification dispatched — alert ID: {}, recipient: {}", alertId, to);
        } catch (Exception ex) {
            log.error("CRITICAL: Failed to dispatch escalation for alert {}: {} (Recipient: {})", 
                      alertId, ex.getMessage(), to, ex);
        }
    }

    // -------------------------------------------------------------------------
    // HTML Template
    // -------------------------------------------------------------------------

    private String buildEmailHtml(Alert alert, String analystName, String customMessage) {
        String severityColor  = resolveSeverityColor(alert.getSeverity());
        String severityBorder = resolveSeverityBorder(alert.getSeverity());
        String timestamp      = formatTimestamp(alert.getTimestamp());

        return "<!DOCTYPE html>" +
            "<html lang=\"en\"><head><meta charset=\"UTF-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<title>RiskTrace — Threat Escalation Notice</title>" +
            "<style>" +
            "  * { box-sizing: border-box; margin: 0; padding: 0; }" +
            "  body { background-color: #f6f8fa; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #24292f; }" +
            "  .wrapper { max-width: 640px; margin: 40px auto; background: #ffffff; border: 1px solid #d0d7de; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }" +
            "  /* Header */" +
            "  .header { background: linear-gradient(135deg, #cf222e 0%, #a40e26 100%); padding: 32px 40px; border-bottom: 4px solid " + severityColor + "; }" +
            "  .header-brand { font-size: 24px; font-weight: 800; color: #ffffff; letter-spacing: -0.3px; }" +
            "  .header-brand span { color: rgba(255,255,255,0.7); }" +
            "  .header-subtitle { margin-top: 4px; font-size: 13px; color: rgba(255,255,255,0.85); text-transform: uppercase; letter-spacing: 1.5px; font-weight: 600; }" +
            "  .severity-pill { display: inline-block; margin-top: 16px; padding: 6px 16px; background: rgba(255,255,255,0.2); color: #fff; font-size: 12px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase; border-radius: 6px; border: 1px solid rgba(255,255,255,0.3); }" +
            "  /* Body */" +
            "  .body { padding: 40px; }" +
            "  .section-title { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.2px; color: #57606a; margin-bottom: 16px; padding-bottom: 8px; border-bottom: 1px solid #d0d7de; }" +
            "  /* Incident metadata table */" +
            "  .meta-table { width: 100%; border-collapse: collapse; margin-bottom: 32px; }" +
            "  .meta-table tr { border-bottom: 1px solid #f6f8fa; }" +
            "  .meta-table tr:last-child { border-bottom: none; }" +
            "  .meta-table td { padding: 12px 0; font-size: 14px; vertical-align: top; }" +
            "  .meta-table td:first-child { color: #57606a; font-weight: 600; width: 160px; padding-right: 16px; }" +
            "  .meta-table td:last-child { color: #24292f; font-weight: 500; }" +
            "  .severity-value { color: " + severityColor + " !important; font-weight: 700 !important; }" +
            "  .mono { font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; font-size: 13px; background: #f6f8fa; padding: 4px 8px; border-radius: 6px; border: 1px solid #d0d7de; color: #0969da; }" +
            "  /* Description block */" +
            "  .desc-block { background: #f6f8fa; border: 1px solid #d0d7de; border-left: 4px solid " + severityColor + "; border-radius: 8px; padding: 20px; margin-bottom: 32px; }" +
            "  .desc-label { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; color: #57606a; margin-bottom: 10px; }" +
            "  .desc-text { font-size: 15px; color: #1f2328; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }" +
            "  /* Alert banner */" +
            "  .action-banner { background: #fff8c5; border: 1px solid rgba(191, 133, 0, 0.2); border-radius: 8px; padding: 18px 22px; margin-bottom: 32px; font-size: 14px; color: #714e00; line-height: 1.6; font-weight: 500; }" +
            "  /* Footer */" +
            "  .footer { background: #f6f8fa; padding: 32px 40px; border-top: 1px solid #d0d7de; text-align: center; }" +
            "  .footer-text { font-size: 12px; color: #57606a; line-height: 1.8; }" +
            "  .footer-text strong { color: #24292f; }" +
            "</style></head><body>" +
            "<div class=\"wrapper\">" +

            // ── Header ────────────────────────────────────────────────────────
            "  <div class=\"header\">" +
            "    <div class=\"header-brand\">Risk<span>Trace</span></div>" +
            "    <div class=\"header-subtitle\">Threat Escalation Notice</div>" +
            "    <div class=\"severity-pill\">" + esc(alert.getSeverity()) + "</div>" +
            "  </div>" +

            // ── Body ──────────────────────────────────────────────────────────
            "  <div class=\"body\">" +

            // Action banner
            "    <div class=\"action-banner\">" +
            "      A security analyst from the RiskTrace SOC has escalated an ML-detected threat to you. " +
            "      Please review the details below and take appropriate action to secure your application." +
            "    </div>" +

            // Analyst description
            "    <div class=\"section-title\">Message from RiskTrace SOC</div>" +
            "    <div class=\"desc-block\">" +
            "      <div class=\"desc-label\">Escalated by: " + esc(nullSafe(analystName, "Unknown Analyst")) + "</div>" +
            "      <div class=\"desc-text\">" + esc(nullSafe(customMessage, "Please investigate this incident immediately.")) + "</div>" +
            "    </div>" +

            // Incident metadata
            "    <div class=\"section-title\">Incident Details</div>" +
            "    <table class=\"meta-table\">" +
            buildRow("Alert ID",       "<span class=\"mono\">" + esc(alert.getId()) + "</span>") +
            buildRow("Type",           esc(alert.getType())) +
            buildRow("Severity",       "<span class=\"severity-value\">" + esc(alert.getSeverity()) + "</span>") +
            buildRow("ML Confidence",  alert.getAnomalyScore() != null ? String.format("%.2f", alert.getAnomalyScore()) : "N/A") +
            buildRow("Timestamp (UTC)", timestamp) +
            "    </table>" +

            // Network context
            "    <div class=\"section-title\">Network Context</div>" +
            "    <table class=\"meta-table\">" +
            buildRow("Source IP",   alert.getSourceIp()   != null ? "<span class=\"mono\">" + esc(alert.getSourceIp()) + "</span>"   : "<em style=\"color:#6e7681\">Not specified</em>") +
            buildRow("Target Path", alert.getTargetPath() != null ? "<span class=\"mono\">" + esc(alert.getTargetPath()) + "</span>" : "<em style=\"color:#6e7681\">Not specified</em>") +
            buildRow("Session ID",  alert.getSessionId()  != null ? "<span class=\"mono\">" + esc(alert.getSessionId()) + "</span>"  : "<em style=\"color:#6e7681\">Not specified</em>") +
            buildRow("Organization", "<span class=\"mono\">" + esc(nullSafe(alert.getOrganizationId(), "N/A")) + "</span>") +
            buildRow("Site",         alert.getSiteId() != null ? "<span class=\"mono\">" + esc(alert.getSiteId()) + "</span>" : "<em style=\"color:#6e7681\">Not specified</em>") +
            "    </table>" +

            "  </div>" +

            // ── Footer ────────────────────────────────────────────────────────
            "  <div class=\"footer\">" +
            "    <p class=\"footer-text\">" +
            "      <strong>RiskTrace Platform</strong><br>" +
            "      This is an automated escalation notice generated on " + timestamp + ".<br>" +
            "      Do not reply to this message. Please check your application logs for more details." +
            "    </p>" +
            "  </div>" +

            "</div></body></html>";
    }

    // -------------------------------------------------------------------------
    // Template utilities
    // -------------------------------------------------------------------------

    private String buildRow(String label, String value) {
        return "<tr>" +
               "<td>" + label + "</td>" +
               "<td>" + value + "</td>" +
               "</tr>";
    }

    private String resolveSeverityColor(String severity) {
        if (severity == null) return "#8b949e";
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "#dc2626";
            case "HIGH"     -> "#ea580c";
            case "MEDIUM"   -> "#d97706";
            case "LOW"      -> "#16a34a";
            default         -> "#8b949e";
        };
    }

    private String resolveSeverityBorder(String severity) {
        if (severity == null) return "rgba(139,148,158,0.2)";
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "rgba(220, 38, 38, 0.2)";
            case "HIGH"     -> "rgba(234, 88, 12, 0.2)";
            case "MEDIUM"   -> "rgba(217, 119, 6, 0.2)";
            case "LOW"      -> "rgba(22, 163, 74, 0.2)";
            default         -> "rgba(139,148,158,0.2)";
        };
    }

    private String formatTimestamp(java.util.Date date) {
        if (date == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date) + " UTC";
    }

    private String esc(String input) {
        if (input == null) return "";
        return input
            .replace("&",  "&amp;")
            .replace("<",  "&lt;")
            .replace(">",  "&gt;")
            .replace("\"", "&quot;")
            .replace("'",  "&#x27;");
    }

    private String nullSafe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
