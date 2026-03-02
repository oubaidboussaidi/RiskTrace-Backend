package com.risktrace.user_service.job;


import com.risktrace.user_service.Repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class TokenCleanupJob {
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        blacklistedTokenRepository.deleteAllByExpiryDateBefore(now);
        log.info("Cleaned up expired blacklisted tokens");
    }
}
