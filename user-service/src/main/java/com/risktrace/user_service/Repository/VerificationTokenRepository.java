package com.risktrace.user_service.Repository;

import com.risktrace.user_service.Model.VerificationToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends MongoRepository<VerificationToken, String> {
    Optional<VerificationToken> findByToken(String token);

    void deleteByUserEmail(String userEmail);
}
