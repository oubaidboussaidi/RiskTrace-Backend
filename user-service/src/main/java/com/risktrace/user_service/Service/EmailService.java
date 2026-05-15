package com.risktrace.user_service.Service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Async
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String link    = frontendUrl + "/verify-email?token=" + token;
        String subject = "RiskTrace | Email Verification Required";
        String html    = renderTemplate("email/verification", fullName, link);
        sendHtmlEmail(toEmail, subject, html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String link    = frontendUrl + "/reset-password?token=" + token;
        String subject = "RiskTrace | Password Reset Request";
        String html    = renderTemplate("email/password-reset", fullName, link);
        sendHtmlEmail(toEmail, subject, html);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String renderTemplate(String templateName, String name, String link) {
        Context ctx = new Context();
        ctx.setVariable("name",        name);
        ctx.setVariable("link",        link);
        return templateEngine.process(templateName, ctx);
    }

    private void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, "RiskTrace Security");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send email to {} (Subject: {}): {}", to, subject, e.getMessage(), e);
        }
    }
}
