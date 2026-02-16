package com.risktrace.alert_service.Repository;

import com.risktrace.alert_service.Model.Alert;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends MongoRepository<Alert, String> {
}
