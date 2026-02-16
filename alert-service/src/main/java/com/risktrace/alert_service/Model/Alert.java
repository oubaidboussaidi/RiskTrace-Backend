package com.risktrace.alert_service.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Document(collection = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    @Id
    private String id;
    private String type; // SQL_INJECTION, BRUTE_FORCE, etc.
    private String severity; // HIGH, MEDIUM, LOW
    private String description;
    private String sourceIp;
    private String targetPath;
    private String status; // OPEN, CLOSED
    private Date timestamp;
}
