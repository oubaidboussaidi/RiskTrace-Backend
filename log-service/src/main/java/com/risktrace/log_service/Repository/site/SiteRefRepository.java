package com.risktrace.log_service.Repository.site;

import com.risktrace.log_service.Model.SiteRef;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SiteRefRepository extends MongoRepository<SiteRef, String> {
    Optional<SiteRef> findByApiKey(String apiKey);
}
