package com.risktrace.log_service.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Log {

    @Id
    private String id;

    // ── Resolved by backend from apiKey lookup ──
    private String siteId; // resolved from apiKey → site._id
    private String organizationId; // resolved from apiKey → site → org

    // ── Sent by tracker script ──
    private String apiKey; // used for lookup, NOT stored long-term (cleared after resolution)
    private String sessionId; // tracker session identifier
    private String type; // page_load | fetch_request | fetch_response | xhr_response
                         // form_submit | js_error | unhandled_promise_rejection | suspicious_activity
    private String url; // request or page URL
    private String method; // GET, POST, PUT, DELETE …
    private Integer statusCode; // HTTP status code (nullable)
    private String userAgent; // raw user-agent string
    private String device; // mobile | tablet | desktop
    private Long responseTime; // ms (nullable)
    private String createdAt; // ISO-8601 from client clock

    // ── Enriched by backend ──
    private String ipAddress; // extracted from request headers
    private String country; // GeoIP lookup (future)
    private String city; // GeoIP lookup (future)

    // ── ML fields (future) ──
    private Double anomalyScore;
    private Boolean isAnomaly;

    // ── Manual Feedback ──
    private Boolean isSuspicious;
}
