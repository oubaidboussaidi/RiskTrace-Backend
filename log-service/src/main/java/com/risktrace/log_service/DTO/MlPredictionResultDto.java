package com.risktrace.log_service.DTO;

/**
 * MlPredictionResultDto
 * ──────────────────────────────────────────────────────────────────────────────
 * Represents one prediction result returned by the RiskTraceML service.
 *
 * Matches the FastAPI PredictionResponse schema:
 *   {
 *     "anomalyScore": float,   // 0.0 = normal, 1.0 = highly anomalous
 *     "prediction":   string,  // "NORMAL" | "ANOMALY"
 *     "confidence":   string   // "LOW" | "MEDIUM" | "HIGH"
 *   }
 * ──────────────────────────────────────────────────────────────────────────────
 */
public record MlPredictionResultDto(
        double anomalyScore,
        String prediction,
        String confidence
) {}
