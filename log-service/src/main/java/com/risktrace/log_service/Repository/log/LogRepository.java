package com.risktrace.log_service.Repository.log;

import com.risktrace.log_service.Model.Log;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogRepository extends MongoRepository<Log, String> {
    List<Log> findBySiteId(String siteId);

    List<Log> findByOrganizationId(String organizationId);

    List<Log> findByType(String type);

    List<Log> findBySiteIdAndType(String siteId, String type);

    List<Log> findTop100BySessionIdOrderByCreatedAtDesc(String sessionId);
}
