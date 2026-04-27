package com.risktrace.log_service.Service;

import com.risktrace.log_service.DTO.MlBatchRequestDto;
import com.risktrace.log_service.DTO.MlBatchResponseDto;
import com.risktrace.log_service.DTO.MlPredictionResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * MlClient
 * ──────────────────────────────────────────────────────────────────────────────
 * HTTP client responsible for communicating with the RiskTraceML FastAPI service.
 *
 * Sends session feature vectors to POST /predict/batch and returns the
 * list of prediction results (one per session).
 *
 * Design decisions:
 *   - Uses a blocking .block() call with a 5-second timeout so LogService
 *     can stay synchronous while still having a hard ceiling on ML latency.
 *   - FAIL-OPEN: any error (timeout, ML down, parse failure) returns an empty
 *     list — logs are already saved and will simply remain unscored rather
 *     than failing the entire /collect request.
 * ──────────────────────────────────────────────────────────────────────────────
 */
@Service
public class MlClient {

    private static final Logger logger = LoggerFactory.getLogger(MlClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String   PREDICT_BATCH_PATH = "/predict/batch";

    private final WebClient webClient;

    public MlClient(@Qualifier("mlWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Send a batch of session feature vectors to the ML service and return
     * the prediction results in the same order as the input sessions.
     *
     * @param request  Batch payload containing one or more SessionFeaturesDto.
     * @return         Ordered list of MlPredictionResultDto, or empty list on failure.
     */
    public List<MlPredictionResultDto> predictBatch(MlBatchRequestDto request) {
        if (request == null || request.sessions() == null || request.sessions().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            logger.info("[MlClient] Sending {} sessions for scoring...", request.sessions().size());

            MlBatchResponseDto response = webClient.post()
                    .uri(PREDICT_BATCH_PATH)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                            logger.error("[MlClient] ML Service Error ({}): {}", clientResponse.statusCode(), errorBody);
                            return Mono.error(new RuntimeException("ML Service returned " + clientResponse.statusCode()));
                        });
                    })
                    .bodyToMono(MlBatchResponseDto.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(ex -> {
                        logger.warn("[MlClient] ML scoring skipped. Reason: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response == null || response.results() == null) {
                logger.warn("[MlClient] ML service returned empty response.");
                return Collections.emptyList();
            }

            logger.info("[MlClient] Successfully received {} scores.", response.total());
            return response.results();

        } catch (Exception ex) {
            logger.error("[MlClient] Critical error during ML call: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}
