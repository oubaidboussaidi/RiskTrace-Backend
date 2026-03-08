package com.risktrace.site_service.Repository;

import com.risktrace.site_service.Model.Site;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends MongoRepository<Site, String> {
    Optional<Site> findByApiKey(String apiKey);

    List<Site> findByUserId(String userId);

    List<Site> findByOrganizationId(String organizationId);
}
