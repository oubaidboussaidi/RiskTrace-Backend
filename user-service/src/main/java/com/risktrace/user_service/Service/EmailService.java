package com.risktrace.user_service.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String subject = "RiskTrace – Verify Your Email Address";
        String html = buildVerificationEmailHtml(fullName, link);
        sendHtmlEmail(toEmail, subject, html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String subject = "RiskTrace – Password Reset Request";
        String html = buildPasswordResetEmailHtml(fullName, link);
        sendHtmlEmail(toEmail, subject, html);
    }

    private void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "RiskTrace Security");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // HTML Email Templates
    // -------------------------------------------------------------------------

    private String buildVerificationEmailHtml(String name, String link) {
        return "<!DOCTYPE html>" +
                "<html lang=\"en\"><head><meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>Verify Your Email</title>" +
                "<style>" +
                "  body { margin: 0; padding: 0; background-color: #0d1117; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }"
                +
                "  .container { max-width: 600px; margin: 40px auto; background: #161b22; border-radius: 12px; overflow: hidden; border: 1px solid #30363d; }"
                +
                "  .header { background: linear-gradient(135deg, #1f6feb, #7c3aed); padding: 40px 40px 32px; text-align: center; }"
                +
                "  .logo { font-size: 28px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px; }" +
                "  .logo span { color: #93c5fd; }" +
                "  .tagline { color: rgba(255,255,255,0.7); font-size: 13px; margin-top: 6px; }" +
                "  .body { padding: 40px; }" +
                "  .greeting { font-size: 22px; font-weight: 700; color: #f0f6fc; margin-bottom: 12px; }" +
                "  .text { font-size: 15px; color: #8b949e; line-height: 1.7; margin-bottom: 20px; }" +
                "  .btn-wrap { text-align: center; margin: 32px 0; }" +
                "  .btn { display: inline-block; background: linear-gradient(135deg, #1f6feb, #7c3aed); color: #ffffff !important; text-decoration: none; padding: 14px 36px; border-radius: 8px; font-size: 15px; font-weight: 600; letter-spacing: 0.3px; }"
                +
                "  .divider { border: none; border-top: 1px solid #30363d; margin: 28px 0; }" +
                "  .link-note { font-size: 13px; color: #6e7681; line-height: 1.6; }" +
                "  .link-url { color: #58a6ff; word-break: break-all; }" +
                "  .footer { background: #0d1117; padding: 24px 40px; text-align: center; }" +
                "  .footer-text { font-size: 12px; color: #484f58; line-height: 1.6; }" +
                "  .expire-notice { background: rgba(56, 189, 248, 0.08); border: 1px solid rgba(56, 189, 248, 0.2); border-radius: 8px; padding: 12px 16px; font-size: 13px; color: #7dd3fc; margin-bottom: 24px; }"
                +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "  <div class=\"header\">" +
                "    <div class=\"logo\">Risk<span>Trace</span></div>" +
                "    <div class=\"tagline\">Security Operations Center</div>" +
                "  </div>" +
                "  <div class=\"body\">" +
                "    <div class=\"greeting\">Hello, " + escapeHtml(name) + "! 👋</div>" +
                "    <p class=\"text\">Welcome to <strong style=\"color:#f0f6fc\">RiskTrace</strong>. You're one step away from accessing the platform. Please verify your email address to activate your account.</p>"
                +
                "    <div class=\"expire-notice\">⏱ This link expires in <strong>24 hours</strong>.</div>" +
                "    <div class=\"btn-wrap\">" +
                "      <a href=\"" + link + "\" class=\"btn\">✅ Verify Email Address</a>" +
                "    </div>" +
                "    <hr class=\"divider\">" +
                "    <p class=\"link-note\">If the button doesn't work, copy and paste this link into your browser:</p>"
                +
                "    <p class=\"link-note\"><a href=\"" + link + "\" class=\"link-url\">" + link + "</a></p>" +
                "    <hr class=\"divider\">" +
                "    <p class=\"link-note\">If you didn't create a RiskTrace account, you can safely ignore this email.</p>"
                +
                "  </div>" +
                "  <div class=\"footer\">" +
                "    <p class=\"footer-text\">© 2025 RiskTrace · All rights reserved<br>This is an automated message, please do not reply.</p>"
                +
                "  </div>" +
                "</div></body></html>";
    }

    private String buildPasswordResetEmailHtml(String name, String link) {
        return "<!DOCTYPE html>" +
                "<html lang=\"en\"><head><meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>Reset Your Password</title>" +
                "<style>" +
                "  body { margin: 0; padding: 0; background-color: #0d1117; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }"
                +
                "  .container { max-width: 600px; margin: 40px auto; background: #161b22; border-radius: 12px; overflow: hidden; border: 1px solid #30363d; }"
                +
                "  .header { background: linear-gradient(135deg, #b91c1c, #7c3aed); padding: 40px 40px 32px; text-align: center; }"
                +
                "  .logo { font-size: 28px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px; }" +
                "  .logo span { color: #fca5a5; }" +
                "  .tagline { color: rgba(255,255,255,0.7); font-size: 13px; margin-top: 6px; }" +
                "  .body { padding: 40px; }" +
                "  .greeting { font-size: 22px; font-weight: 700; color: #f0f6fc; margin-bottom: 12px; }" +
                "  .text { font-size: 15px; color: #8b949e; line-height: 1.7; margin-bottom: 20px; }" +
                "  .btn-wrap { text-align: center; margin: 32px 0; }" +
                "  .btn { display: inline-block; background: linear-gradient(135deg, #b91c1c, #7c3aed); color: #ffffff !important; text-decoration: none; padding: 14px 36px; border-radius: 8px; font-size: 15px; font-weight: 600; letter-spacing: 0.3px; }"
                +
                "  .divider { border: none; border-top: 1px solid #30363d; margin: 28px 0; }" +
                "  .link-note { font-size: 13px; color: #6e7681; line-height: 1.6; }" +
                "  .link-url { color: #58a6ff; word-break: break-all; }" +
                "  .footer { background: #0d1117; padding: 24px 40px; text-align: center; }" +
                "  .footer-text { font-size: 12px; color: #484f58; line-height: 1.6; }" +
                "  .warning-notice { background: rgba(239, 68, 68, 0.08); border: 1px solid rgba(239, 68, 68, 0.25); border-radius: 8px; padding: 12px 16px; font-size: 13px; color: #fca5a5; margin-bottom: 24px; }"
                +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "  <div class=\"header\">" +
                "    <div class=\"logo\">Risk<span>Trace</span></div>" +
                "    <div class=\"tagline\">Security Operations Center</div>" +
                "  </div>" +
                "  <div class=\"body\">" +
                "    <div class=\"greeting\">Password Reset Request 🔐</div>" +
                "    <p class=\"text\">Hi <strong style=\"color:#f0f6fc\">" + escapeHtml(name)
                + "</strong>, we received a request to reset the password for your RiskTrace account.</p>" +
                "    <div class=\"warning-notice\">⚠️ This link expires in <strong>1 hour</strong>. If you didn't request a password reset, please ignore this email — your account is safe.</div>"
                +
                "    <div class=\"btn-wrap\">" +
                "      <a href=\"" + link + "\" class=\"btn\">🔑 Reset My Password</a>" +
                "    </div>" +
                "    <hr class=\"divider\">" +
                "    <p class=\"link-note\">If the button doesn't work, copy and paste this link into your browser:</p>"
                +
                "    <p class=\"link-note\"><a href=\"" + link + "\" class=\"link-url\">" + link + "</a></p>" +
                "    <hr class=\"divider\">" +
                "    <p class=\"link-note\">For security, this link can only be used once. If you need a new link, go to the login page and use the &quot;Forgot Password&quot; option again.</p>"
                +
                "  </div>" +
                "  <div class=\"footer\">" +
                "    <p class=\"footer-text\">© 2025 RiskTrace · All rights reserved<br>This is an automated message, please do not reply.</p>"
                +
                "  </div>" +
                "</div></body></html>";
    }

    private String escapeHtml(String input) {
        if (input == null)
            return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
