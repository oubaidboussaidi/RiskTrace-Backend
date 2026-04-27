package com.risktrace.log_service.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SessionFeaturesDto
 * ──────────────────────────────────────────────────────────────────────────────
 * Represents one session's aggregated behavioural feature vector.
 *
 * Field names and types MUST match the FastAPI SessionFeatures Pydantic schema
 * in RiskTraceML/src/api/main.py exactly, because they are serialized to JSON
 * and sent directly to POST /predict/batch.
 *
 * All 12 features are computed by SessionAggregator from a List<Log>.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public record SessionFeaturesDto(

        @JsonProperty("request_count")
        double request_count,

        @JsonProperty("error_rate")
        double error_rate,

        @JsonProperty("auth_failure_count")
        double auth_failure_count,

        @JsonProperty("avg_response_time_ms")
        double avg_response_time_ms,

        @JsonProperty("p95_response_time_ms")
        double p95_response_time_ms,

        @JsonProperty("unique_endpoints")
        double unique_endpoints,

        @JsonProperty("unique_ips")
        double unique_ips,

        @JsonProperty("anomalous_path_count")
        double anomalous_path_count,

        @JsonProperty("post_ratio")
        double post_ratio,

        @JsonProperty("js_error_count")
        double js_error_count,

        @JsonProperty("request_rate")
        double request_rate,

        @JsonProperty("session_duration_s")
        double session_duration_s

) {}
