package com.risktrace.user_service.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.risktrace.user_service.Enums.OrganizationRole;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_members")
public class OrganizationMember {

    @Id
    private String id;

    private String userId;

    private String organizationId;

    private OrganizationRole role;

    @CreatedDate
    private Instant createdAt;
}
