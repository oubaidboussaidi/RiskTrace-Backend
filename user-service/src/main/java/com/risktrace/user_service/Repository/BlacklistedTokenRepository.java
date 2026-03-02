package com.risktrace.user_service.Repository;

import com.risktrace.user_service.Model.BlacklistedToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface BlacklistedTokenRepository extends MongoRepository<BlacklistedToken, String> {
    Optional<BlacklistedToken> findByToken(String token);
    boolean existsByToken(String token);
    void deleteAllByExpiryDateBefore(Instant now);
}
