package com.risktrace.log_service.Service;

import com.risktrace.log_service.DTO.SessionFeaturesDto;
import com.risktrace.log_service.Model.Log;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SessionAggregator
 * ──────────────────────────────────────────────────────────────────────────────
 * Java-side mirror of feature_engineering.py::aggregate_session_logs().
 *
 * Given a list of Log documents belonging to the same sessionId, computes
 * the exact 12 behavioural features the Isolation Forest model was trained on.
 *
 * Feature order and computation MUST stay consistent with:
 *   RiskTraceML/src/ml/feature_engineering.py  (aggregate_session_logs)
 *   RiskTraceML/src/ml/preprocessing.py         (FEATURE_COLUMNS)
 * ──────────────────────────────────────────────────────────────────────────────
 */
@Service
public class SessionAggregator {

    // Paths that indicate reconnaissance / attack activity — mirrors ANOMALOUS_PATH_PATTERNS in Python
    private static final Pattern ANOMALOUS_PATH_PATTERN = Pattern.compile(
            "(?i)(/admin|/actuator|\\.env|/wp-admin|/phpmyadmin|\\.git|/config)"
    );

    // HTTP status codes counted as errors — mirrors ERROR_STATUS_CODES in Python
    private static final Set<Integer> ERROR_STATUS_CODES = Set.of(
            400, 401, 403, 404, 405, 429, 500, 502, 503, 504
    );

    // Auth-specific failure codes
    private static final Set<Integer> AUTH_FAILURE_CODES = Set.of(401, 403);

    // Minimum session duration in seconds (avoid division by zero)
    private static final double MIN_DURATION_S = 1.0;

    /**
     * Aggregate a list of Log documents (one session window) into a
     * SessionFeaturesDto ready to be sent to the ML /predict/batch endpoint.
     *
     * @param logs  All Log entries sharing the same sessionId. Must not be empty.
     * @return      A SessionFeaturesDto with all 12 computed features.
     */
    public SessionFeaturesDto aggregate(List<Log> logs) {
        if (logs == null || logs.isEmpty()) {
            return zeroFeatures();
        }

        int requestCount = logs.size();

        int errorCount       = 0;
        int authFailureCount = 0;
        int postCount        = 0;
        int jsErrorCount     = 0;
        int anomalousCount   = 0;

        List<Double> responseTimes = new ArrayList<>(requestCount);
        Set<String>  endpoints     = new HashSet<>();
        Set<String>  ips           = new HashSet<>();

        Instant minTime = null;
        Instant maxTime = null;

        for (Log log : logs) {

            // ── Status code ──────────────────────────────────────────────────
            int statusCode = log.getStatusCode() != null ? log.getStatusCode() : 200;
            if (ERROR_STATUS_CODES.contains(statusCode)) errorCount++;
            if (AUTH_FAILURE_CODES.contains(statusCode)) authFailureCount++;

            // ── Response time ────────────────────────────────────────────────
            double rt = log.getResponseTime() != null ? (double) log.getResponseTime() : 0.0;
            responseTimes.add(rt);

            // ── URL / endpoint ───────────────────────────────────────────────
            String url = log.getUrl() != null ? log.getUrl() : "";
            endpoints.add(url);
            if (ANOMALOUS_PATH_PATTERN.matcher(url).find()) anomalousCount++;

            // ── IP address ───────────────────────────────────────────────────
            String ip = log.getIpAddress() != null ? log.getIpAddress() : "";
            ips.add(ip);

            // ── Method ───────────────────────────────────────────────────────
            String method = log.getMethod() != null ? log.getMethod().toUpperCase() : "";
            if ("POST".equals(method)) postCount++;

            // ── Log type (JS errors from tracker.js) ─────────────────────────
            String type = log.getType() != null ? log.getType() : "";
            if ("js_error".equals(type) || "unhandled_promise_rejection".equals(type)) jsErrorCount++;

            // ── Timestamps for duration ──────────────────────────────────────
            Instant ts = parseTimestamp(log.getCreatedAt());
            if (ts != null) {
                if (minTime == null || ts.isBefore(minTime)) minTime = ts;
                if (maxTime == null || ts.isAfter(maxTime))  maxTime = ts;
            }
        }

        // ── Derived metrics ──────────────────────────────────────────────────
        double duration = computeDuration(minTime, maxTime);
        double avgRt    = average(responseTimes);
        double p95Rt    = percentile(responseTimes, 95);

        return new SessionFeaturesDto(
                (double) requestCount,
                requestCount > 0 ? (double) errorCount / requestCount : 0.0,
                (double) authFailureCount,
                avgRt,
                p95Rt,
                (double) endpoints.size(),
                (double) ips.size(),
                (double) anomalousCount,
                requestCount > 0 ? (double) postCount / requestCount : 0.0,
                (double) jsErrorCount,
                requestCount / duration,   // request_rate = req/s
                duration
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SessionFeaturesDto zeroFeatures() {
        return new SessionFeaturesDto(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, MIN_DURATION_S
        );
    }

    private Instant parseTimestamp(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) return null;
        try {
            return Instant.parse(createdAt);
        } catch (Exception e) {
            return null;
        }
    }

    private double computeDuration(Instant min, Instant max) {
        if (min == null || max == null || !max.isAfter(min)) return MIN_DURATION_S;
        double secs = (double) (max.toEpochMilli() - min.toEpochMilli()) / 1000.0;
        return Math.max(secs, MIN_DURATION_S);
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    /**
     * Computes the p-th percentile of a list using nearest-rank method.
     * Mirrors numpy.percentile() behaviour used in Python feature_engineering.
     */
    private double percentile(List<Double> values, int p) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
