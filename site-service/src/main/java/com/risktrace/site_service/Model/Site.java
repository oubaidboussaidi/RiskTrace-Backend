package com.risktrace.site_service.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Document(collection = "sites")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Site {
    @Id
    private String id;
    private String userId; // owner — resolved from JWT at creation time
    private String organizationId;
    private String siteName; // matches spec field name
    private String domain;
    private String apiKey;
    private String status; // ACTIVE, INACTIVE
    private Date createdAt;
    private Date lastActive;
}
