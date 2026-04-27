package com.risktrace.log_service.DTO;

import java.util.List;

/**
 * MlBatchRequestDto
 * ──────────────────────────────────────────────────────────────────────────────
 * Request body sent to POST /predict/batch on the RiskTraceML FastAPI service.
 *
 * Matches the FastAPI BatchSessionFeatures Pydantic schema:
 *   { "sessions": [ <SessionFeatures>, ... ] }
 * ──────────────────────────────────────────────────────────────────────────────
 */
public record MlBatchRequestDto(List<SessionFeaturesDto> sessions) {}
