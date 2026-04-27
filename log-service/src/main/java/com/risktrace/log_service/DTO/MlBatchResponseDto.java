package com.risktrace.log_service.DTO;

import java.util.List;

/**
 * MlBatchResponseDto
 * ──────────────────────────────────────────────────────────────────────────────
 * Outer response wrapper returned by POST /predict/batch.
 *
 * Matches the FastAPI BatchPredictionResponse schema:
 *   {
 *     "results": [ <PredictionResponse>, ... ],
 *     "total":   int
 *   }
 *
 * results[i] corresponds to sessions[i] in the request — order is preserved.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public record MlBatchResponseDto(
        List<MlPredictionResultDto> results,
        int total
) {}
